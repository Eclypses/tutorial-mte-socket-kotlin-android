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
// FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
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
import com.eclypses.ecdh.EcdhP256
import com.eclypses.mte.MteBase
import com.eclypses.mte.MteDec
import com.eclypses.mte.MteEnc
import com.eclypses.mte.MteStatus
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.UUID
import java.util.concurrent.CountDownLatch


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
    private const val DEFAULT_IP_ADDRESS = "*** Server IP ***"
    private const val DEFAULT_PORT = 27015

    //---------------------------------------------------------------------
    // These strings are needed if you are using a licensed version of MTE.
    //---------------------------------------------------------------------
    private const val licenseCompanyName = "Eclypses Inc"
    private const val licenseKey = "Eclypses123"

    //--------------------------------------------------------------
    // Helper function to get an instance handle to the application.
    //--------------------------------------------------------------
    private var singleton: MyApplication? = null
    fun getInstance(): MyApplication? {
      return singleton
    }
  }

  class SetupParams {
    var ipAddress: String? = null
    var port = 0
  }

  private class MteSetupInfo {
    var personalization: String? = null     // The personalization string.
    var nonce: ByteArray? = null            // The nonce.
    var ecdh: EcdhP256? = null              // DH instance used to generate the shared secret.
    var myPublicKey: ByteArray? = null      // This entity's public DH key.
    var peerPublicKey: ByteArray? = null    // The public key received from its peer.
    var mySecret: ByteArray? = null         // This entity's secret; NEVER SHARE THE SECRET!
  }

  //-----------------------------------------------------
  // We need separate setup info for Encoder and Decoder
  // and an instance of our EclypsesECDH class which will
  // create key pairs and shared secrets.
  //-----------------------------------------------------
  private var encoderSetupInfo = MteSetupInfo()
  private var decoderSetupInfo = MteSetupInfo()

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
  //private static final int fixedBytes = 8;
  enum class ReceiveMode {
    NONE, EXPECT_ACK, GET_PARAMS, WAIT_MESSAGE
  }

  private val TAG = this.javaClass.simpleName
  private var initDone = false
  private var socket: Socket? = null
  private var socketOpen = false
  private var sockIn: InputStream? = null
  private var sockOut: OutputStream? = null
  private var socketCallback: SocketCallback? = null
  private var transmitBusy: CountDownLatch? = null
  private var setupParams: SetupParams? = null
  private var initValuesReceived = 0


  //------------------------------------------------------------------------------------------
  // onCreate():
  // Android creates an instance of "MyApplication" when it launches the app. This instance
  // will survive even the destruction of the main activity most of the times. It will NEVER
  // be instantiated more than once.
  // Instantiation takes places before the main activity is created. Any activity of the app
  // must never instantiate MyApplication! In order to access functions of MyApplication,
  // these functions must be declared static or called by means of utilizing the "Instance"
  // value (see companion object).
  //------------------------------------------------------------------------------------------
  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG)
      Log.d(TAG, "onCreate() Enter")
    singleton = this
    setupParams = SetupParams()
    setupParams!!.ipAddress = DEFAULT_IP_ADDRESS
    setupParams!!.port = DEFAULT_PORT
    socket = null
    sockIn = null
    sockOut = null
    socketOpen = false
    socketCallback = null
    transmitBusy = null
    encoderSetupInfo.mySecret = null
    encoderSetupInfo.nonce = null
    encoderSetupInfo.personalization = null
    encoderSetupInfo.myPublicKey = null
    encoderSetupInfo.peerPublicKey = null
    encoderSetupInfo.ecdh = null
    decoderSetupInfo.mySecret = null
    decoderSetupInfo.nonce = null
    decoderSetupInfo.personalization = null
    decoderSetupInfo.myPublicKey = null
    decoderSetupInfo.peerPublicKey = null
    decoderSetupInfo.ecdh = null
    encoder = null
    decoder = null
    initDone = false
    if (BuildConfig.DEBUG)
      Log.d(TAG, "onCreate() Exit")
  }


  //-----------------------------------------------------------------------
  // Helper function to get the current setup parameters (for SetupDialog).
  //-----------------------------------------------------------------------
  fun getSetupParams(): SetupParams {
    return setupParams!!
  }


  //-----------------------------------------------------------------------
  // Helper function to check if socket communication has been established.
  //-----------------------------------------------------------------------
  fun isCommOpen(): Boolean {
    return socket != null
  }


  //-------------------------------------------------------------------------
  // terminate() sets everything back to an unconfigured state so that init()
  // could be called again. The activity in charge of managing the app may
  // call this function if it detects that it is being finished by the OS.
  //-------------------------------------------------------------------------
  fun terminate() {
    if (BuildConfig.DEBUG)
      Log.d(TAG, "terminate()")
    if (!initDone) {
      if (BuildConfig.DEBUG)
        Log.d(TAG, "terminate(): failed")
      return
    }
    setupParams!!.ipAddress = DEFAULT_IP_ADDRESS
    setupParams!!.port = DEFAULT_PORT
    encoder = null
    decoder = null
    initDone = false
  }


  //--------------------------------------------------------------------------
  // setupMTE() is initially called from the main activity with a parameter
  // value of "null". This will jumpstart the initialization process. Due to
  // the strict requirements of Android that any communication must be run
  // outside the main UI thread, the "sendToServer()" function will run its
  // sending of data in a separate thread.
  // If an answer to a transmission is expected from the server side,
  // "sendToServer()" will create another thread to wait for the answer and
  // that thread will either post the received data to the main activity or
  // will call back to setupMTE() right here.
  //
  // The flow of communication for the setup runs like this:
  //
  // The main activity calls "setupMTE(null)".
  //   setupMTE() checks the MTE license (step #1).
  //   setupMTE() creates separate 256-bit elliptic curve key pairs and also
  //     personalization strings for Encoder and Decoder (step #2).
  //   setupMTE() sends the public keys and the personalization strings to
  //     the server side. After all four values have been sent, setupMTE()
  //     expects an answer (an "acknowledge") from the side. In order to get
  //     this done, "sendToServer()" will create a thread which runs the
  //     "receiveFromServer()" function which will call back into "setupMTE()"
  //     with the returned data from the server.
  //
  //   When setupMTE() is called and supplied with received data, it will
  //   analyze the data and act accordingly. After sending out the four values
  //   to the server, "setupMTE()" expects to be called with the "ACK" answer
  //   (step #4). Once that "ACK" is received, "setupMTE()" will start to
  //   receive the four values from the server side. These values are the
  //   public keys and the nonces for Encoder and Decoder.
  //
  //   Once all four values have been received, "setupMTE()" creates the
  //   shared secrets for Encoder and Decoder (step #5).
  //   After that, "setupMTE()" will create the Encoder (step #6). Please
  //   make sure that you comment/uncomment the code in step #6 accordingly
  //   depending on what kind of MTE functionality you want to use (Core,
  //   FLEN or MKE). Also make sure to comment/uncomment the declaration of
  //   "encoder" at the top of this file accordingly.
  //   Step #6A will then set entropy (the shared DH secret) and nonce (which
  //   was received from the server) for the Encoder. Step #7B finally will
  //   instantiate the Encoder using the personalization string created in
  //   step #2.
  //   Steps #7, #7A and 7B will repeat the process of creation, setting
  //   entropy and nonce and do instantiation for the Decoder.
  //
  //   After all these steps have been completed successfully, "setupMTE()"
  //   will send an "ACK" to the server side and signal to the main activity
  //   that communication is up and running. On receiving this notification,
  //   the main activity will send a simple "ping" message to the server to
  //   demonstrate that the communication is working. In a real world setup,
  //   a client would probably transmit login credentials.
  //--------------------------------------------------------------------------
  fun setupMTE(dataReceived: ByteArray?): Boolean {
    if (initDone) {
      if (BuildConfig.DEBUG)
        Log.d(TAG, "setupMTE(): already initialized")
      return false
    }
    if (dataReceived == null) {
      //----------------------------------------------------------
      // Step #1
      // Initialize MTE license. If a license code is not required
      // (e.g. trial mode), this can be skipped.
      //----------------------------------------------------------
      if (!MteBase.initLicense(licenseCompanyName, licenseKey)) {
        cleanupMTE()
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): MTE license check failed")
        return false
      }
      //----------------------------------------------------------
      // Step #2
      // Create personalization strings for Encoder and Decoder.
      // Also create the 2 key pairs needed for Diffie-Hellman.
      //----------------------------------------------------------
      encoderSetupInfo.ecdh = EcdhP256()
      encoderSetupInfo.myPublicKey = ByteArray(EcdhP256.SzPublicKey)
      if (encoderSetupInfo.ecdh!!.createKeyPair(encoderSetupInfo.myPublicKey) != EcdhP256.Success) {
        encoderSetupInfo.ecdh = null
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): creating encoder keys failed")
      }
      decoderSetupInfo.ecdh = EcdhP256()
      decoderSetupInfo.myPublicKey = ByteArray(EcdhP256.SzPublicKey)
      if (decoderSetupInfo.ecdh!!.createKeyPair(decoderSetupInfo.myPublicKey) != EcdhP256.Success) {
        decoderSetupInfo.ecdh = null
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): creating decoder keys failed")
      }
      if (encoderSetupInfo.myPublicKey == null || decoderSetupInfo.myPublicKey == null) {
        cleanupMTE()
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): Key pairs generation failed")
        return false
      }
      encoderSetupInfo.personalization = UUID.randomUUID().toString()
      decoderSetupInfo.personalization = UUID.randomUUID().toString()
      //--------------------------------------------------------
      // Step #3
      // Send the personalization strings and public keys out to
      // the server.
      //--------------------------------------------------------
      sendToServer('1'.code, encoderSetupInfo.myPublicKey, ReceiveMode.NONE)
      sendToServer('2'.code, encoderSetupInfo.personalization!!.toByteArray(StandardCharsets.UTF_8),
                   ReceiveMode.NONE)
      sendToServer('3'.code, decoderSetupInfo.myPublicKey, ReceiveMode.NONE)
      sendToServer('4'.code, decoderSetupInfo.personalization!!.toByteArray(StandardCharsets.UTF_8),
                   ReceiveMode.EXPECT_ACK)
      initValuesReceived = 0
      return true
    } else if (dataReceived.isNotEmpty()) {
      var abort = false
      val message = Arrays.copyOfRange(dataReceived, 1, dataReceived.size)
      when (dataReceived[0].toInt().toChar()) {
        'A' -> {
          if (String(message) == "ACK") {
            //-----------------------------------------------
            // Step #4
            // Server acknowledged our parameters. Let's
            // get started receiving the server's parameters.
            //-----------------------------------------------
            initValuesReceived = 0
          } else {
            // This is an "ERR" message but we skip checking all characters here
            if (BuildConfig.DEBUG)
              Log.d(TAG, "setupMTE(): server did not acknowledge initial params")
            abort = true
          }
        }
        'E' -> {
          if (BuildConfig.DEBUG)
            Log.d(TAG, "setupMTE(): server did not acknowledge initial params")
          abort = true
        }
        '1' -> {
          //---------------------------------------------
          // We received the server's public Encoder key.
          // We'll use it to create our Decoder secret.
          //---------------------------------------------
          if (decoderSetupInfo.peerPublicKey == null)
            initValuesReceived++
          decoderSetupInfo.peerPublicKey = message
        }
        '2' -> {
          //----------------------------------------
          // We received the server's Encoder nonce.
          // We'll use it for our Decoder.
          //----------------------------------------
          if (decoderSetupInfo.nonce == null)
            initValuesReceived++
          decoderSetupInfo.nonce = message
        }
        '3' -> {
          //---------------------------------------------
          // We received the server's public Decoder key.
          // We'll use it to create our Encoder secret.
          //---------------------------------------------
          if (encoderSetupInfo.peerPublicKey == null)
            initValuesReceived++
          encoderSetupInfo.peerPublicKey = message
        }
        '4' -> {
          //----------------------------------------
          // We received the server's Decoder nonce.
          // We'll use it for our Encoder.
          //----------------------------------------
          if (encoderSetupInfo.nonce == null)
            initValuesReceived++
          encoderSetupInfo.nonce = message
        }
        else -> {
          if (BuildConfig.DEBUG)
            Log.d(TAG, "setupMTE(): internal software error")
          abort = true
        }
      }
      if (abort) {
        cleanupMTE()
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): error receiving initial parameters")
        return false
      }
      if (initValuesReceived < 4) {
        receiveFromServer(ReceiveMode.GET_PARAMS)
        return true
      }
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "------------------------------------------------------------")
        Log.d(TAG, "Encoder public key      " + bytesToHex(encoderSetupInfo.myPublicKey))
        Log.d(TAG, "Encoder peer's key      " + bytesToHex(encoderSetupInfo.peerPublicKey))
        Log.d(TAG, "Encoder nonce           " + bytesToHex(encoderSetupInfo.nonce))
        Log.d(TAG, "Encoder personalization " + encoderSetupInfo.personalization)
        Log.d(TAG, "------------------------------------------------------------")
        Log.d(TAG, "Decoder public key      " + bytesToHex(decoderSetupInfo.myPublicKey))
        Log.d(TAG, "Decoder peer's key      " + bytesToHex(decoderSetupInfo.peerPublicKey))
        Log.d(TAG, "Decoder nonce           " + bytesToHex(decoderSetupInfo.nonce))
        Log.d(TAG, "Decoder personalization " + decoderSetupInfo.personalization)
        Log.d(TAG, "------------------------------------------------------------")
      }
      //-----------------------------------------------------------
      // Step #5
      // Now that we have all the data we need, let's
      // create our Encoder and Decoder entropies. Note that we are
      // using the peer's Encoder key to create the entropy for our
      // Decoder and vice versa.
      //-----------------------------------------------------------
      encoderSetupInfo.mySecret = ByteArray(EcdhP256.SzSecretData)
      if (encoderSetupInfo.ecdh!!.getSharedSecret(encoderSetupInfo.peerPublicKey,
                      encoderSetupInfo.mySecret) != EcdhP256.Success) {
        cleanupMTE()
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): error generating shared secret for Encoder")
        return false
      }
      EcdhP256.zeroize(encoderSetupInfo.myPublicKey)
      encoderSetupInfo.myPublicKey = null
      EcdhP256.zeroize(encoderSetupInfo.peerPublicKey)
      encoderSetupInfo.peerPublicKey = null
      if (BuildConfig.DEBUG)
        Log.d(TAG, "Encoder secret = " + bytesToHex(encoderSetupInfo.mySecret))
      decoderSetupInfo.mySecret = ByteArray(EcdhP256.SzSecretData)
      if (decoderSetupInfo.ecdh!!.getSharedSecret(decoderSetupInfo.peerPublicKey,
                                                  decoderSetupInfo.mySecret) != EcdhP256.Success) {
        cleanupMTE()
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): error generating shared secret for Decoder")
        return false
      }
      EcdhP256.zeroize(decoderSetupInfo.myPublicKey)
      decoderSetupInfo.myPublicKey = null
      EcdhP256.zeroize(decoderSetupInfo.peerPublicKey)
      decoderSetupInfo.peerPublicKey = null
      if (BuildConfig.DEBUG)
        Log.d(TAG, "Decoder secret = " + bytesToHex(decoderSetupInfo.mySecret))
      //-------------------------------------------------
      // Step #6
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
      //encoder = new MteMkeEnc();
      //---------------------------------------------------------------
      // Create the MTE Fixed length Encoder, uncomment to use MTE FLEN
      //---------------------------------------------------------------
      //encoder = new MteFlenEnc(fixedBytes);
      //
      //--------------------------------------
      // Step #6A
      // Set Entropy and Nonce for our Encoder
      //--------------------------------------
      encoder!!.setEntropy(encoderSetupInfo.mySecret)
      Arrays.fill(encoderSetupInfo.mySecret!!, 0.toByte())
      encoderSetupInfo.mySecret = null
      encoder!!.setNonce(encoderSetupInfo.nonce)
      encoderSetupInfo.nonce = null
      //------------------------
      // Step #6B
      // Instantiate the Encoder
      //------------------------
      if (encoder!!.instantiate(encoderSetupInfo.personalization) != MteStatus.mte_status_success) {
        cleanupMTE()
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): error instantiating MTE Encoder")
        return false
      }
      encoderSetupInfo.personalization = null
      //-------------------------------------------------
      // Step #7
      // Create Decoder with default options
      //
      // MKE and FLEN add-ons are NOT part of all SDK MTE
      // versions, the name of the SDK will contain
      // "-MKE" or "-FLEN" if it has these add-ons.
      //-------------------------------------------------
      //
      //----------------------------------------------------------
      // Create the MTE Decoder, uncomment to use MTE Core or FLEN
      // Create the MTE Fixed length Decoder (SAME as MTE Core)
      //----------------------------------------------------------
      decoder = MteDec(0, 0)
      //-------------------------------------------------
      // Create the MTE MKE Decoder, uncomment to use MKE
      //-------------------------------------------------
      //decoder = new MteMkeDec();
      //
      //--------------------------------------
      // Step #7A
      // Set Entropy and Nonce for our Decoder
      //--------------------------------------
      decoder!!.setEntropy(decoderSetupInfo.mySecret)
      Arrays.fill(decoderSetupInfo.mySecret!!, 0.toByte())
      decoderSetupInfo.mySecret = null
      decoder!!.setNonce(decoderSetupInfo.nonce)
      decoderSetupInfo.nonce = null
      //------------------------
      // Step #7B
      // Instantiate the Decoder
      //------------------------
      if (decoder!!.instantiate(decoderSetupInfo.personalization) != MteStatus.mte_status_success) {
        cleanupMTE()
        if (BuildConfig.DEBUG)
          Log.d(TAG, "setupMTE(): error instantiating MTE Decoder")
        return false
      }
      decoderSetupInfo.personalization = null
      if (BuildConfig.DEBUG)
        Log.d(TAG, "setupMTE(): initialization completed")
      sendToServer('A'.code, "ACK".toByteArray(StandardCharsets.UTF_8), ReceiveMode.NONE)
      postAnswerToApp("Ready".toByteArray(StandardCharsets.UTF_8))
      return true
    }
    return false
  }


  private fun cleanupMTE() {
    encoderSetupInfo.ecdh = null
    if (encoderSetupInfo.myPublicKey != null)
      EcdhP256.zeroize(encoderSetupInfo.myPublicKey)
    encoderSetupInfo.myPublicKey = null
    if (encoderSetupInfo.peerPublicKey != null)
      EcdhP256.zeroize(encoderSetupInfo.peerPublicKey)
    encoderSetupInfo.peerPublicKey = null
    if (encoderSetupInfo.mySecret != null)
      EcdhP256.zeroize(encoderSetupInfo.mySecret)
    encoderSetupInfo.mySecret = null
    encoderSetupInfo.nonce = null
    encoderSetupInfo.personalization = null
    decoderSetupInfo.ecdh = null
    if (decoderSetupInfo.myPublicKey != null)
      EcdhP256.zeroize(decoderSetupInfo.myPublicKey)
    decoderSetupInfo.myPublicKey = null
    if (decoderSetupInfo.peerPublicKey != null)
      EcdhP256.zeroize(decoderSetupInfo.peerPublicKey)
    decoderSetupInfo.peerPublicKey = null
    if (decoderSetupInfo.mySecret != null)
      EcdhP256.zeroize(decoderSetupInfo.mySecret)
    decoderSetupInfo.mySecret = null
    decoderSetupInfo.nonce = null
    decoderSetupInfo.personalization = null
    encoder = null
    decoder = null
  }


  //-----------------------------------------------------------------
  // openCommunication() creates and opens a socket to the server and
  // sets shortcuts for the input and output streams.
  // It also registers the instance of "whatever" will receive the
  // callbacks.
  //-----------------------------------------------------------------
  fun openCommunication(newSetupParams: SetupParams,
                        socketCallback: SocketCallback): Boolean {
    if (BuildConfig.DEBUG)
      Log.d(TAG, "openCommunication(): Enter")
    if (socket != null) {
      if (BuildConfig.DEBUG)
        Log.d(TAG, "openCommunication(): Exit, socket already open")
      return false
    }
    // Store the connection parameters
    setupParams = newSetupParams
    // Store the instance providing the callback function
    this.socketCallback = socketCallback
    transmitBusy = null
    //----------------------------------------------------------------
    // We have to run the opening of the socket in a different thread,
    // otherwise Android will throw a "NetworkOnMainThreadException"
    //----------------------------------------------------------------
    val openingThread = Thread(Runnable {
      // Create and open the network socket
      try {
        socket = Socket(setupParams!!.ipAddress, setupParams!!.port)
        sockIn = socket!!.getInputStream()
        sockOut = socket!!.getOutputStream()
      } catch (e: IOException) {
        if (BuildConfig.DEBUG)
          Log.d(TAG, "openCommunication(): IOException creating socket and/or readers/writers")
        postAnswerToApp("Error".toByteArray(StandardCharsets.UTF_8))
        return@Runnable
      }
      postAnswerToApp("Ready".toByteArray(StandardCharsets.UTF_8))
      socketOpen = true
    })
    openingThread.start()
    if (BuildConfig.DEBUG)
      Log.d(TAG, "openCommunication(): Exit, trying to connect")
    return true
  }


  //---------------------------------------------------------
  // closeCommunication() closes the socket to the server and
  // clears all associated variables.
  // We don't need an extra thread to close a socket!
  //---------------------------------------------------------
  fun closeCommunication() {
    if (!socketOpen) return
    try {
      socket!!.close()
    } catch (e: IOException) {
      if (BuildConfig.DEBUG)
        Log.d(TAG, "closeCommunication(): IOException closing socket")
    }
    socket = null
    sockIn = null
    sockOut = null
    transmitBusy = null
    socketOpen = false
  }


  //----------------------------------------------------------------------------
  // sendToServer() will send the given string to the server and will also try
  // to get an answer. Processing the answer will run in a separate thread and
  // ultimately issue a callback to the application.
  //----------------------------------------------------------------------------
  fun sendToServer(header: Int, data: ByteArray?, receiveMode: ReceiveMode) {
    if (BuildConfig.DEBUG)
      Log.d(TAG, "sendToServer(): Enter")
    if (transmitBusy != null) {
      try {
        transmitBusy!!.await()
      } catch (e: InterruptedException) {
        if (BuildConfig.DEBUG)
          Log.d(TAG, "sendToServer(): thread interrupted waiting for socket")
      }
    }
    transmitBusy = CountDownLatch(1)
    //--------------------------------------------------------------
    // We must run the writing to the socket in a different thread,
    // otherwise Android will throw a "NetworkOnMainThreadException"
    //--------------------------------------------------------------
    val sendingThread = Thread(Runnable {
      val msgHeader = ByteBuffer.allocate(5)
      // The default byte order of a ByteBuffer in Java is always
      // BIG Endian, which is what we want - but here we go anyway!
      msgHeader.order(ByteOrder.BIG_ENDIAN)
      msgHeader.putInt(data!!.size)
      msgHeader.put(header.toByte())
      try {
        sockOut!!.write(msgHeader.array())
        sockOut!!.write(data)
      } catch (e: IOException) {
        if (BuildConfig.DEBUG)
          Log.d(TAG, "sendToServer(): exception writing to socket")
        return@Runnable
      }
      if (BuildConfig.DEBUG)
        Log.d(TAG, "sendToServer(): data sent to server")
      transmitBusy!!.countDown()
      if (receiveMode != ReceiveMode.NONE) {
        //------------------------------------------------------------------
        // Reading the answer from the socket must be in a another extra
        // thread, just to avoid the dreaded "NetworkOnMainThreadException".
        //------------------------------------------------------------------
        receiveFromServer(receiveMode)
      }
    })
    sendingThread.start()
  }


  private fun receiveFromServer(receiveMode: ReceiveMode) {
    if (receiveMode == ReceiveMode.NONE) return
    //---------------------------------------------------------------
    // Reading from a socket must be done from within the main thread
    // to avoid the dreaded "NetworkOnMainThreadException".
    //---------------------------------------------------------------
    val receivingThread = Thread(Runnable {
      val receiveAction: ReceiveMode = receiveMode
      if (BuildConfig.DEBUG)
        Log.d(TAG, "receiveFromServer(): Enter")
      val dataLengthRx = ByteArray(4)
      var i = 0
      var rd: Int
      try {
        while (i < 4) {
          rd = sockIn!!.read(dataLengthRx, i, 4 - i)
          if (rd < 0) {
            if (BuildConfig.DEBUG)
              Log.d(TAG, "receiveFromServer(): socket read abort")
            return@Runnable
          }
          i += rd
        }
      } catch (e: IOException) {
        if (BuildConfig.DEBUG)
          Log.d(TAG, "receiveFromServer(): socket read exception")
        return@Runnable
      }
      //---------------------------------------------------------------
      // The default byte order of a ByteBuffer is always BIG Endian!
      // And that is what we want anyway. So we spare us the extra work
      // of setting up a named ByteBuffer instantiation and setting
      // up the byte order to ByteOrder.BIG_ENDIAN;
      //---------------------------------------------------------------
      val c = ByteBuffer.wrap(dataLengthRx).int + 1
      val dataBytes = ByteArray(c)
      i = 0
      try {
        while (i < c) {
          rd = sockIn!!.read(dataBytes, i, c - i)
          if (rd < 0) {
            if (BuildConfig.DEBUG)
              Log.d(TAG, "receiveFromServer(): socket read abort")
            return@Runnable
          }
          i += rd
        }
      } catch (e: IOException) {
        if (BuildConfig.DEBUG)
          Log.d(TAG, "receiveFromServer(): socket read exception")
        return@Runnable
      }
      when (receiveAction) {
        ReceiveMode.EXPECT_ACK, ReceiveMode.GET_PARAMS -> {
          //-------------------------------------------------------------
          // setupMTE() was originally called from the main UI thread,
          // so we need to get back on that thread for the next recursive
          // call to setupMTE().
          //-------------------------------------------------------------
          val handler = Handler(Looper.getMainLooper())
          handler.post { setupMTE(dataBytes) }
        }
        ReceiveMode.WAIT_MESSAGE -> postAnswerToApp(dataBytes)
        else -> {}
      }
      if (BuildConfig.DEBUG)
        Log.d(TAG, "receiveFromServer(): Exit")
    })
    receivingThread.start()
  }


  //----------------------------------------------------------------------------
  // postAnswerToApp() will run the callback to get server's answers and other
  // information back to the app. It is up to the app to interpret the context
  // of the answers sent.
  // In order to make sure that the app can access its views within the callback
  // function, we make sure to run it on the main UI thread.
  //----------------------------------------------------------------------------
  private fun postAnswerToApp(data: ByteArray) {
    if (socketCallback == null)
      return
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
    if (result.status == MteStatus.mte_status_success)
      return result.arr
    else {
      if (BuildConfig.DEBUG)
        Log.d(TAG, "encodeData(): failed, error = " + MteBase.getStatusDescription(result.status))
      return null
    }
  }


  //-----------------------------------------------------
  // decodeData() will decode the given string using MTE.
  //-----------------------------------------------------
  fun decodeData(data: ByteArray?): ByteArray? {
    val result = decoder!!.decode(data)
    if (!MteBase.statusIsError(result.status))
      return result.arr
    else {
      if (BuildConfig.DEBUG)
        Log.d(TAG, "decodeData(): failed, error = " + MteBase.getStatusDescription(result.status))
      return null
    }
  }


  //-------------------------------------------------------------------------
  // getVersion() is a simple wrapper just so that the application
  // can get the MTE version number without having to import the MTE package.
  // We also add the MTE variant (Core, MKE, FLEN) to the version number.
  // Please note that adding the MTE variant requires that "encoder" has been
  // created.
  //-------------------------------------------------------------------------
  fun getVersion(): String {
    var s = " MTE " + MteBase.getVersion()
    if (encoder != null) {
      s += " ("
      when (encoder!!.javaClass.simpleName) {
        "MteEnc" -> s += "Core"
        "MteMkeEnc" -> s += "MKE"
        "MteFlenEnc" -> s += "FLEN"
        else -> {}
      }
      s += ")"
    }
    return s
  }


  //-------------------------------------------------------------------------
  // This little helper function converts a byte array of arbitrary length
  // to a hex string.
  //-------------------------------------------------------------------------
  @Suppress("MemberVisibilityCanBePrivate")
  fun bytesToHex(bytes: ByteArray?): String {
    val hex = StringBuilder()
    for (b in bytes!!) hex.append(String.format("%02X", b))
    return hex.toString()
  }
}
