//**************************************************************************************************
// The MIT License (MIT)
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



import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.eclypses.mte.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.*



//---------------------------------------------------------------------------------------
// NOTE TO THE SOFTWARE DEVELOPER:
//
// This application source code is educational material. In order to keep the source code
// as short as possible, many internal and unlikely errors are not communicated to the
// upper layers of the user interface.
// It is therefore suggested to run this app in an emulator or at least on a connected
// device with a live logcat facility in order to catch internal error messages which are
// written to logcat using the "debug" log level.
//---------------------------------------------------------------------------------------
class MyApplication : Application() {
  companion object {
    //-------------------------------
    // Default communication settings
    //-------------------------------
    private const val DEFAULT_IP_ADDRESS = "*** SERVER'S IP HERE! ***"
    private const val DEFAULT_PORT = 27015

    //---------------------------------------------------------------------
    // These strings are needed if you are using a licensed version of MTE.
    // Checking the license can be omitted for demo/trial versions of MTE
    // but is included in this source code for demonstration purposes.
    //---------------------------------------------------------------------
    private const val licenseCompanyName = "Eclypses Inc"
    private const val licenseKey = "Eclypses123"

    //------------------------------------------------------------------
    // Set default entropy, nonce and identifier
    // Providing entropy in this fashion is insecure. This is for
    // demonstration purposes only and should never be done in practice.
    // This is a trial version of the MTE, so entropy must be blank.
    //------------------------------------------------------------------
    private const val DEFAULT_ENTROPY = ""
    private const val DEFAULT_ENCODER_NONCE: Long = 1

    //----------------------------------------------------------------------------------------
    // OPTIONAL!!! Adding 1 to Encoder nonce so return value changes -- same nonce can be used
    // for Encoder and Decoder. On server side values will have to be switched so they match
    // up Encoder to Decoder and vice versa.
    //----------------------------------------------------------------------------------------
    private const val DEFAULT_DECODER_NONCE: Long = 0
    private const val DEFAULT_PERSONALIZATION = "demo"

    //---------------------------------------------------------------------
    // This helper function supplies an instance handle to the application.
    //---------------------------------------------------------------------
    var singleton: MyApplication? = null

    fun getInstance(): MyApplication? {
      return singleton
    }
  }

  inner class SetupParams {
    var ipAddress: String = ""
    var port = 0
  }

  //-------------------------------------------------
  // MKE and FLEN add-ons are NOT part of all SDK MTE
  // versions, the name of the SDK will contain
  // "-MKE" or "-FLEN" if it has these add-ons.
  //--------------------------------------------------
  //
  //-------------------------------------
  // Declare the MTE Encoder and Decoder,
  // uncomment to use MTE Core.
  //-------------------------------------
  private var encoder: MteEnc? = null
  private var decoder: MteDec? = null
  //-------------------------------------
  // Declare the MTE Encoder and Decoder,
  // uncomment to use MKE.
  //-------------------------------------
  //private var encoder: MteMkeEnc? = null
  //private var decoder: MteMkeDec? = null
  //-------------------------------------
  // Declare the MTE Encoder and Decoder,
  // uncomment to use MTE FLEN. Note that
  // MTE FLEN uses the standard MTE Core
  // Decoder.
  //-------------------------------------
  //private var encoder: MteFlenEnc? = null
  //private var decoder: MteDec? = null
  //-------------------------------------------------
  // Fixed length parameter needed for using MTE FLEN
  // Uncomment this line if you are using MTE FLEN.
  //-------------------------------------------------
  //private val fixedBytes = 8


  private val TAG = MyApplication::class.java.simpleName
  private var initDone = false
  private var socket: Socket? = null
  private var socketOpen = false
  private var sockIn: InputStream? = null
  private var sockOut: OutputStream? = null
  private var socketCallback: SocketCallback? = null
  private var setupParams: SetupParams? = null


  //------------------------------------------------------------------------------------------
  // onCreate():
  // Android creates an instance of "MyApplication" when it launches the app. This instance
  // will survive even the destruction of the main activity most of the times. It will NEVER
  // be instantiated more than once.
  // Instantiation takes places before the main activity is created. Any activity of the app
  // must never instantiate MyApplication! In order to access functions of MyApplication,
  // these functions must be declared static or called by means of utilizing the "getInstance"
  // function (see below).
  //------------------------------------------------------------------------------------------
  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "onCreate() Enter")
    singleton = this
    setupParams = SetupParams()
    setupParams!!.ipAddress = DEFAULT_IP_ADDRESS
    setupParams!!.port = DEFAULT_PORT
    encoder = null
    decoder = null
    socket = null
    sockIn = null
    sockOut = null
    socketOpen = false
    socketCallback = null
    initDone = false
    Log.d(TAG, "onCreate() Exit")
  }


  fun getSetupParams(): SetupParams {
    return setupParams!!
  }


  fun isInitDone(): Boolean {
    return initDone
  }


  //------------------------------------------------------------------------
  // init() is called by the activity in charge of managing the app in order
  // to do basic initialization. The parameter is a set of settings used for
  // communication to the server and to initialize MTE.
  // init() calls setupMTE() to have Encoder and Decoder created.
  //------------------------------------------------------------------------
  fun init(newSetupParams: SetupParams?): Boolean {
    if (initDone) {
      Log.d(TAG, "init(): already initialized")
      return false
    }
    Log.d(TAG, "init(): processing")
    setupParams = newSetupParams
    initDone = setupMTE()
    return initDone
  }


  //-------------------------------------------------------------------------
  // terminate() sets everything back to an unconfigured state so that init()
  // could be called again. The activity in charge of managing the app may
  // call this function if it detects that it is being finished by the OS.
  //-------------------------------------------------------------------------
  fun terminate() {
    Log.d(TAG, "terminate()")
    if (!initDone) {
      Log.d(TAG, "terminate(): failed")
      return
    }
    closeCommunication()
    setupParams!!.ipAddress = DEFAULT_IP_ADDRESS
    setupParams!!.port = DEFAULT_PORT
    encoder = null
    decoder = null
    initDone = false
  }


  //--------------------------------------------------------------------------
  // setupMTE() is called from init() and will initialize all parameters
  // needed to run MTE; then it will create the Encoder and Decoder.
  //--------------------------------------------------------------------------
  private fun setupMTE(): Boolean {
    //------------------------------------------------------------------------------
    // Step #1
    // Initialize MTE license. If a license code is not required (e.g., trial mode),
    // this can be skipped.
    //------------------------------------------------------------------------------
    var rc = true
    if (!MteBase.initLicense(licenseCompanyName, licenseKey)) {
      Log.d(TAG, "init(): MTE license check failed")
      rc = false
    }
    var entropy = DEFAULT_ENTROPY
    if (rc) {
      //-------------------------------------------------
      // Step #2
      // Create Encoder with default options
      //
      // MKE and FLEN add-ons are NOT part of all SDK MTE
      // versions, the name of the SDK will contain
      // "-MKE" or "-FLEN" if it has these add-ons.
      //-------------------------------------------------
      //
      //--------------------------------------------------
      // Create the MTE Encoder, uncomment to use MTE Core
      //--------------------------------------------------
      encoder = MteEnc()
      //-------------------------------------------------
      // Create the MTE MKE Encoder, uncomment to use MKE
      //-------------------------------------------------
      //encoder = MteMkeEnc()
      //---------------------------------------------------------------
      // Create the MTE Fixed length Encoder, uncomment to use MTE FLEN
      //---------------------------------------------------------------
      //encoder = MteFlenEnc(fixedBytes);
      //
      //--------------------------------------------------------------------
      // Calculate a default entropy based on the drbg requirements:
      // CAUTION: the entropy is one of the secret values to initialize MTE.
      //          In a real world environment, entropy should be supplied in
      //          a secure way, like calculating a shared secret using
      //          Diffie-Hellman.
      //--------------------------------------------------------------------
      val drbg = encoder!!.drbg
      val entropyMinBytes = MteBase.getDrbgsEntropyMinBytes(drbg)
      val entropyMaxBytes = MteBase.getDrbgsEntropyMaxBytes(drbg)
      if (entropyMaxBytes == 0L) entropy = ""
      val d = entropyMinBytes - entropy.length
      if (d > 0) {
        // Add '0' characters to satisfy minimum entropy length
        val chars = CharArray(d)
        Arrays.fill(chars, '0')
        entropy += String(chars)
      } else if (entropy.length - entropyMaxBytes > 0) {
        // Strip characters to enforce maximum entropy length
        entropy = entropy.substring(0, entropyMaxBytes.toInt())
      }
      //----------------------
      // Step #3
      // Set Entropy and Nonce
      //----------------------
      encoder!!.setEntropy(entropy.toByteArray(StandardCharsets.UTF_8))
      encoder!!.setNonce(DEFAULT_ENCODER_NONCE)
      //------------------------
      // Step #4
      // Instantiate the Encoder
      //------------------------
      if (encoder!!.instantiate(DEFAULT_PERSONALIZATION) != MteStatus.mte_status_success) {
        Log.d(TAG, "init(): instantiation of MTE Encoder failed")
        rc = false
      }
    }
    if (rc) {
      //-------------------------------------------------
      // Step #5
      // Create Decoder with default options
      //
      // MKE and FLEN add-ons are NOT part of all SDK MTE
      // versions, the name of the SDK will contain
      // "-MKE" or "-FLEN" if it has these add-ons.
      //-------------------------------------------------
      //
      //----------------------------------------------------------
      // Create the MTE Encoder, uncomment to use MTE Core or FLEN
      // Create the MTE Fixed length Decoder (SAME as MTE Core)
      //----------------------------------------------------------
      decoder = MteDec()
      //-------------------------------------------------
      // Create the MTE MKE Encoder, uncomment to use MKE
      //-------------------------------------------------
      //decoder = MteMkeDec()
      //
      //----------------------
      // Step #6
      // Set Entropy and Nonce
      //----------------------
      decoder!!.setEntropy(entropy.toByteArray(StandardCharsets.UTF_8))
      decoder!!.setNonce(DEFAULT_DECODER_NONCE)
      //------------------------
      // Step #7
      // Instantiate the Decoder
      //------------------------
      if (decoder!!.instantiate(DEFAULT_PERSONALIZATION) != MteStatus.mte_status_success) {
        Log.d(TAG, "init(): instantiation of MTE Decoder failed")
        rc = false
      }
    }
    if (!rc) {
      encoder = null
      decoder = null
    }
    return rc
  }


  //-----------------------------------------------------------------
  // openCommunication() creates and opens a socket to the server and
  // sets shortcuts for the input and output streams.
  // It also registers the instance of "whatever" will receive the
  // callbacks.
  //-----------------------------------------------------------------
  fun openCommunication(socketCallback: SocketCallback?) {
    Log.d(TAG, "openCommunication(): Enter")
    if (!initDone) {
      Log.d(TAG, "openCommunication(): Exit #1")
      return
    }
    // Cleanup if we have an open socket
    closeCommunication()
    // Store the instance providing the callback function
    this.socketCallback = socketCallback
    //----------------------------------------------------------------
    // We have to run the opening of the socket in a different thread,
    // otherwise Android will throw a "NetworkOnMainThreadException"
    //----------------------------------------------------------------
    val openingThread = Thread openingThread@{
      // Create and open the network socket
      try {
        socket = Socket(setupParams!!.ipAddress, setupParams!!.port)
        sockIn = socket!!.getInputStream()
        sockOut = socket!!.getOutputStream()
      } catch (e: IOException) {
        Log.d(TAG, "openCommunication(): IOException creating socket and/or readers/writers")
        postAnswerToApp("FALSE".toByteArray(StandardCharsets.UTF_8))
        return@openingThread
      }
      postAnswerToApp("TRUE".toByteArray(StandardCharsets.UTF_8))
      socketOpen = true
    }
    openingThread.start()
    Log.d(TAG, "openCommunication(): Exit #2")
  }


  //---------------------------------------------------------
  // closeCommunication() closes the socket to the server and
  // clears all associated variables.
  // We don't need an extra thread to close a socket!
  //---------------------------------------------------------
  private fun closeCommunication() {
    if (!socketOpen) return
    try {
      socket!!.close()
    } catch (e: IOException) {
      Log.d(TAG, "closeCommunication(): IOException closing socket")
    }
    socket = null
    sockIn = null
    sockOut = null
    socketOpen = false
  }


  //----------------------------------------------------------------------------
  // sendToServer() will send the given string to the server and will also try
  // to get an answer. Processing the answer will run in a separate thread and
  // ultimately issue a callback to the application.
  //----------------------------------------------------------------------------
  fun sendToServer(data: ByteArray) {
    Log.d(TAG, "sendToServer(): Enter")
    //--------------------------------------------------------------
    // We must run the writing to the socket in a different thread,
    // otherwise Android will throw a "NetworkOnMainThreadException"
    //--------------------------------------------------------------
    val sendingThread = Thread sendingThread@{
      val dataLengthTx = ByteBuffer.allocate(4)
      // The default byte order of a ByteBuffer is always BIG Endian,
      // which is what we want - but here we go anyway!
      dataLengthTx.order(ByteOrder.BIG_ENDIAN)
      dataLengthTx.putInt(data.size)
      try {
        sockOut!!.write(dataLengthTx.array())
        sockOut!!.write(data)
      } catch (e: IOException) {
        Log.d(TAG, "sendToServer(): exception writing to socket")
        return@sendingThread
      }
      Log.d(TAG, "sendToServer(): data sent to server")
      //------------------------------------------------------------------
      // Reading the answer from the socket must be in a another extra
      // thread, just to avoid the dreaded "NetworkOnMainThreadException".
      //------------------------------------------------------------------
      val receivingThread = Thread receivingThread@{
        Log.d(TAG, "sendToServer(): [receiver thread] trying to read")
        val dataLengthRx = ByteArray(4)
        var i = 0
        var rd: Int
        try {
          while (i < 4) {
            rd = sockIn!!.read(dataLengthRx, i, 4 - i)
            if (rd < 0) {
              Log.d(TAG, "sendToServer(): [receiver thread] abort reading from socket")
              return@receivingThread
            }
            i += rd
          }
        } catch (e: IOException) {
          Log.d(TAG, "sendToServer(): [receiver thread] exception reading from socket")
          return@receivingThread
        }
        // The default byte order of a ByteBuffer is always BIG Endian!
        // And that is what we want anyway. So we spare us the extra work
        // of setting up a named ByteBuffer instantiation and setting
        // up the byte order to ByteOrder.BIG_ENDIAN;
        val c = ByteBuffer.wrap(dataLengthRx).int
        val dataBytes = ByteArray(c)
        i = 0
        try {
          while (i < c) {
            rd = sockIn!!.read(dataBytes, i, c - i)
            if (rd < 0) {
              Log.d(TAG, "sendToServer(): [receiver thread] abort reading from socket")
              return@receivingThread
            }
            i += rd
          }
        } catch (e: IOException) {
          Log.d(TAG, "sendToServer(): [receiver thread] exception reading from socket")
          return@receivingThread
        }
        Log.d(TAG, "sendToServer(): [receiver thread] reading completed")
        postAnswerToApp(dataBytes)
      }
      receivingThread.start()
    }
    sendingThread.start()
  }


  //----------------------------------------------------------------------------
  // postAnswerToApp() will run the callback to get server's answers and other
  // information back to the app. It is up to the app to interpret the context
  // of the answers sent.
  // In order to make sure that the app can access its views within the callback
  // function, we make sure to run it on the main UI thread.
  //----------------------------------------------------------------------------
  private fun postAnswerToApp(data: ByteArray) {
    if (socketCallback == null) return
    //-----------------------------------------------------------------
    // The registered callback function must run in the main UI thread!
    // Otherwise, any UI related functions will cause exceptions.
    //
    // Therefore ... get a handler to the main UI thread ...
    //-----------------------------------------------------------------
    val handler = Handler(Looper.getMainLooper())
    // ... and run the callback there!
    //--------------------------------
    handler.post { socketCallback!!.answerFromServer(data) }
  }


  //-----------------------------------------------------
  // encodeData() will encode the given string using MTE.
  //-----------------------------------------------------
  fun encodeData(data: ByteArray?): ByteArray? {
    val result = encoder!!.encode(data)
    return if (result.status == MteStatus.mte_status_success) result.arr else {
      Log.d(TAG, "encodeData(): failed, error = " + MteBase.getStatusDescription(result.status))
      null
    }
  }


  //-----------------------------------------------------
  // decodeData() will decode the given string using MTE.
  //-----------------------------------------------------
  fun decodeData(data: ByteArray?): ByteArray? {
    val result = decoder!!.decode(data)
    return if (result.status == MteStatus.mte_status_success) result.arr else {
      Log.d(TAG, "decodeData(): failed, error = " + MteBase.getStatusDescription(result.status))
      null
    }
  }


  //-------------------------------------------------------------------------
  // getVersion() is a simple wrapper just so that the application
  // can get the MTE version number without having to import the MTE package.
  //-------------------------------------------------------------------------
  fun getVersion(): String {
    var s = " MTE " + MteBase.getVersion()
    if (encoder != null) {
      s += " ("
      when (encoder!!.javaClass.simpleName) {
        "MteEnc" -> s += "Core"
        "MteMkeEnc" -> s += "MKE"
        "MteFlenEnc" -> s += "FLEN"
        else -> {
        }
      }
      s += ")"
    }
    return s
  }
}