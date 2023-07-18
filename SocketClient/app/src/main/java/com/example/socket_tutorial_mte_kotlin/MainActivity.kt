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
// FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//**************************************************************************************************
package com.example.socket_tutorial_mte_kotlin

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.nio.charset.StandardCharsets
import java.util.Arrays


class MainActivity : AppCompatActivity(), SocketCallback {

  companion object {
    private const val SETUP_DIALOG = 100
  }

  private val TAG = this.javaClass.simpleName
  private var myApp: MyApplication? = null

  internal enum class CommStatus {
    Offline, Opening, Connected, Secured, Wait4Answer
  }

  private var commStatus: CommStatus? = null


  @SuppressLint("ClickableViewAccessibility")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (BuildConfig.DEBUG)
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
    if (!myApp!!.isCommOpen()) {
      // Run the communication setup dialog
      val i = Intent(this, SetupDialog::class.java)
      val setupParams = myApp!!.getSetupParams()
      i.putExtra("ipAddress", setupParams.ipAddress)
      i.putExtra("port", setupParams.port)
      @Suppress("DEPRECATION")
      startActivityForResult(i, SETUP_DIALOG)
    } else {
      // Communication is already open,
      // so lets run the MTE setup.
      if (!myApp!!.setupMTE(null)) init(false)
    }
    if (BuildConfig.DEBUG)
      Log.d(TAG, "OnCreate() Exit")
  }


  private fun init(ok: Boolean) {
    var tv: TextView
    // Setup the text areas part 2
    // Start communication or toast an MTE init error
    if (ok) {
      supportActionBar!!.subtitle = myApp!!.getVersion()
      tv = findViewById(R.id.textViewEnc)
      tv.setBackgroundColor(getColor(R.color.green))
      tv.setText(R.string.enc_ready)
      tv = findViewById(R.id.textViewDec)
      tv.setBackgroundColor(getColor(R.color.green))
      tv.setText(R.string.dec_ready)
      commStatus = CommStatus.Secured
    } else {
      tv = findViewById(R.id.textViewEnc)
      tv.setBackgroundColor(getColor(R.color.red))
      tv.setText(R.string.enc_not_ready)
      tv = findViewById(R.id.textViewDec)
      tv.setBackgroundColor(getColor(R.color.red))
      tv.setText(R.string.dec_not_ready)
      commStatus = CommStatus.Offline
      Toast.makeText(this, R.string.mte_init_failed, Toast.LENGTH_LONG).show()
    }
  }


  //---------------------------------------------------------------------
  // Our child activity (setup dialog) has terminated. Decide what to do.
  //---------------------------------------------------------------------
  @Deprecated("Deprecated in Java")
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    @Suppress("DEPRECATION")
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode == SETUP_DIALOG && resultCode == RESULT_OK) {
      val setupParams = myApp!!.getSetupParams()
      setupParams.ipAddress = data!!.getStringExtra("ipAddress")
      setupParams.port = data.getIntExtra("port", setupParams.port)
      commStatus = CommStatus.Opening
      if (!myApp!!.openCommunication(setupParams, this)) init(false)
      return
    }
    init(false)
  }


  //-----------------------------------------------------------------------
  // Our activity is being destroyed. If it is actually being finished off,
  // we call the closeCommunication() and terminate() functions to cleanup.
  //-----------------------------------------------------------------------
  override fun onDestroy() {
    if (isFinishing) {
      myApp!!.closeCommunication()
      myApp!!.terminate()
    }
    super.onDestroy()
  }


  fun sendDataClicked(view: View) {
    if (!view.isEnabled)
      return
    if (BuildConfig.DEBUG)
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
      if (BuildConfig.DEBUG)
        Log.d(TAG, "sendDataClicked() Exit")
      return
    } else {
      // We take the "encoded" byte array and convert it to a Base64 string
      // so we can show it here for demonstration purposes.
      tv.setBackgroundColor(getColor(R.color.green))
      tv.text = Base64.encodeToString(encoded, Base64.NO_WRAP)
    }
    commStatus = CommStatus.Wait4Answer
    myApp!!.sendToServer('m'.code, encoded, MyApplication.ReceiveMode.WAIT_MESSAGE)
    view.isEnabled = false
    if (BuildConfig.DEBUG)
      Log.d(TAG, "sendDataClicked() Exit")
  }


  @SuppressLint("SetTextI18n")
  override fun answerFromServer(data: ByteArray) {
    val s: String
    if (BuildConfig.DEBUG)
      Log.d(TAG, "answerFromServer() Enter")
    val userInput = findViewById<EditText>(R.id.userInput)
    when (commStatus) {
      CommStatus.Opening -> {
        s = String(data)
        if (s == "Ready") {
          commStatus = CommStatus.Connected
          if (BuildConfig.DEBUG)
            Log.d(TAG, "answerFromServer(): connection established")
        } else {
          commStatus = CommStatus.Offline
          if (BuildConfig.DEBUG)
            Log.d(TAG, "answerFromServer(): " + String(data))
        }
        if (commStatus == CommStatus.Offline)
          init(false)
        else
          myApp!!.setupMTE(null)
      }
      CommStatus.Connected -> {
        s = String(data)
        if (s == "Ready") {
          commStatus = CommStatus.Secured
          if (BuildConfig.DEBUG)
            Log.d(TAG, "answerFromServer(): communication secured")
        } else {
          commStatus = CommStatus.Offline
          if (BuildConfig.DEBUG)
            Log.d(TAG, "answerFromServer(): " + String(data))
        }
        init(commStatus == CommStatus.Secured)
        //---------------------------------------------------
        // Communication is secured, now let's do a ping test
        //---------------------------------------------------
        userInput.setText("ping")
        sendDataClicked(userInput)
      }
      CommStatus.Wait4Answer -> {
        // Try to decode the response
        val decoded = myApp!!.decodeData(Arrays.copyOfRange(data, 1, data.size))
        // Check the result and update the fields
        // We take the "data" byte array and convert it to a Base64 string
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
        commStatus = CommStatus.Secured
        userInput.isEnabled = true
        findViewById<View>(R.id.button).isEnabled = true
      }
      else -> if (BuildConfig.DEBUG)
                Log.d(TAG, "answerFromServer() unknown communication status")
    }
    Log.d(TAG, "answerFromServer() Exit")
  }
}
