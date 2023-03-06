//**************************************************************************************************
// THIS SOFTWARE MAY NOT BE USED FOR PRODUCTION.
//
// Copyright (c) Eclypses, Inc.
//
// All rights reserved.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//**************************************************************************************************
package com.example.socket_tutorial_mte_kotlin



import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import java.nio.charset.StandardCharsets


class MainActivity : AppCompatActivity(), SocketCallback {
  companion object {
    private const val SETUP_DIALOG = 100
  }


  private val TAG = this.javaClass.simpleName
  private var myApp: MyApplication? = null

  enum class CommStatus {
    Disabled, WaitOpen, Connected, Wait4Answer
  }
  private var commStatus: CommStatus? = null


  @SuppressLint("ClickableViewAccessibility")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate() Enter")
    myApp = MyApplication.getInstance()
    setContentView(R.layout.activity_main)
    title = getString(R.string.app_name)
    // Implement the clear button functionality for userInput.
    val userInput = findViewById<EditText>(R.id.userInput)
    userInput.setOnTouchListener { v: View, ev: MotionEvent ->
      val et = v as EditText
      if (et === userInput) {
        if (ev.x >= et.width - et.totalPaddingRight) {
          if (ev.action == MotionEvent.ACTION_UP) {
            et.setText("")
          }
        }
      }
      false
    }
    // Implement an automatic call to send the data when userInput is done.
    userInput.setOnEditorActionListener { _: TextView?, actionId: Int, _: KeyEvent? ->
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        sendDataClicked(findViewById(R.id.button))
      }
      false
    }
    // Setup the text areas, part 1
    var tv = findViewById<TextView>(R.id.textViewEnc)
    tv.setTextColor(getColor(R.color.white))
    tv = findViewById(R.id.textViewRecvd)
    tv.setTextColor(getColor(R.color.white))
    tv.setBackgroundColor(getColor(R.color.green))
    tv = findViewById(R.id.textViewDec)
    tv.setTextColor(getColor(R.color.white))
    // And disable the "data send" button for now
    findViewById<View>(R.id.button).isEnabled = false
    // Get an instance handle to MyMTE and take care of the
    // system initialization
    if (!myApp!!.isInitDone()) {
      // Run the setup dialog
      val i = Intent(this, SetupDialog::class.java)
      val setupParams = myApp!!.getSetupParams()
      i.putExtra("ipAddress", setupParams.ipAddress)
      i.putExtra("port", setupParams.port)
      @Suppress("DEPRECATION")
      startActivityForResult(i, SETUP_DIALOG)
    } else {
      // Setup is good so lets continue with initialization
      init(true)
    }
    Log.d(TAG, "OnCreate() Exit")
  }


  private fun init(ok: Boolean) {
    var tv: TextView
    // Setup the text areas part 2
    // Start communication or toast an MTE init error
    if (ok) {
      supportActionBar?.subtitle = myApp!!.getVersion()
      tv = findViewById(R.id.textViewEnc)
      tv.setBackgroundColor(getColor(R.color.green))
      tv.setText(R.string.enc_ready)
      tv = findViewById(R.id.textViewDec)
      tv.setBackgroundColor(getColor(R.color.green))
      tv.setText(R.string.dec_ready)
      commStatus = CommStatus.WaitOpen
      myApp!!.openCommunication(this)
      checkConnectStatus()
    } else {
      tv = findViewById(R.id.textViewEnc)
      tv.setBackgroundColor(getColor(R.color.red))
      tv.setText(R.string.enc_not_ready)
      tv = findViewById(R.id.textViewDec)
      tv.setBackgroundColor(getColor(R.color.red))
      tv.setText(R.string.dec_not_ready)
      commStatus = CommStatus.Disabled
      Toast.makeText(this, R.string.mte_init_failed, Toast.LENGTH_LONG).show()
    }
  }


  //---------------------------------------------------------------------
  // Our child activity (setup dialog) has terminated. Decide what to do.
  //---------------------------------------------------------------------
  @Suppress("OVERRIDE_DEPRECATION")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    @Suppress("DEPRECATION")
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == SETUP_DIALOG && resultCode == RESULT_OK) {
      val setupParams = myApp!!.getSetupParams()
      setupParams.ipAddress = data!!.getStringExtra("ipAddress").toString()
      setupParams.port = data.getIntExtra("port", setupParams.port)
      init(myApp!!.init(setupParams))
    }
  }


  //-----------------------------------------------------------------------
  // Our activity is being destroyed. If it is actually being finished off,
  // we call our MyMTE.terminate() function to cleanup.
  //-----------------------------------------------------------------------
  override fun onDestroy() {
    if (isFinishing) myApp!!.terminate()
    super.onDestroy()
  }


  private fun checkConnectStatus() {
    when (commStatus) {
      CommStatus.WaitOpen -> {
        Log.d(TAG, "checkConnectStatus(): WaitOpen")
        Handler(Looper.myLooper()!!).postDelayed({ checkConnectStatus() }, 1000)
      }
      CommStatus.Connected -> {
        findViewById<View>(R.id.button).isEnabled = true
        Toast.makeText(this, R.string.connected, Toast.LENGTH_LONG).show()
      }
      CommStatus.Disabled -> {
        findViewById<View>(R.id.button).isEnabled = false
        Toast.makeText(this, R.string.not_connected, Toast.LENGTH_LONG).show()
      }
      else -> {}
    }
  }


  fun sendDataClicked(view: View) {
    if (!view.isEnabled) return
    Log.d(TAG, "sendDataClicked() Enter")
    findViewById<View>(R.id.userInput).clearFocus() // just take the cursor away from userInput
    // Encode the plaintext
    val userInput = findViewById<EditText>(R.id.userInput)
    val encoded = myApp!!.encodeData(userInput.text.toString().toByteArray(StandardCharsets.UTF_8))
    // Check the status and update the Encoder text field
    var tv = findViewById<TextView>(R.id.textViewRecvd)
    tv.text = ""
    tv = findViewById(R.id.textViewDec)
    tv.text = ""
    tv = findViewById(R.id.textViewEnc)
    if (encoded == null) {
      tv.setBackgroundColor(getColor(R.color.red))
      tv.setText(R.string.enc_no_encode)
      Log.d(TAG, "sendDataClicked() Exit")
      return
    } else {
      // We take the "encoded" byte array and convert it to a "hex" string
      // so we can show it here for demonstration purposes.
      tv.setBackgroundColor(getColor(R.color.green))
      tv.text = Base64.encodeToString(encoded, Base64.NO_WRAP)
    }
    commStatus = CommStatus.Wait4Answer
    myApp!!.sendToServer(encoded)
    view.isEnabled = false
    Log.d(TAG, "sendDataClicked() Exit")
  }


  override fun answerFromServer(data: ByteArray) {
    Log.d(TAG, "answerFromServer() Enter")
    when (commStatus) {
      CommStatus.WaitOpen -> {
        val s = String(data)
        commStatus = if (s == "TRUE") CommStatus.Connected else CommStatus.Disabled
      }
      CommStatus.Connected -> Log.d(TAG, "answerFromServer(): " + String(data))
      CommStatus.Wait4Answer -> {
        // Try to decode the response
        val decoded: ByteArray? = myApp!!.decodeData(data)
        // check the result and update the fields
        // We take the "data" byte array and convert it to a "hex" string
        // so we can show it here for demonstration purposes.
        var tv = findViewById<TextView>(R.id.textViewRecvd)
        tv.text = Base64.encodeToString(data, Base64.NO_WRAP)
        tv = findViewById(R.id.textViewDec)
        if (decoded == null) {
          tv.setBackgroundColor(getColor(R.color.red))
          tv.setText(R.string.dec_no_decode)
        } else {
          tv.setBackgroundColor(getColor(R.color.green))
          tv.text = String(decoded)
        }
        commStatus = CommStatus.Connected
        findViewById<View>(R.id.button).isEnabled = true
      }
      else -> Log.d(TAG, "answerFromServer() unknown communication status")
    }
    Log.d(TAG, "answerFromServer() Exit")
  }
}
