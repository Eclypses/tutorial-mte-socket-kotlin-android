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

import android.content.Intent
import android.os.Bundle
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AlignmentSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity


class SetupDialog : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val ss = SpannableString(getString(R.string.app_name) + " " + getString(R.string.setup))
    ss.setSpan(ForegroundColorSpan(getColor(R.color.primary)),
               0, ss.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    ss.setSpan(TypefaceSpan("sans-serif-condensed"),
               0, ss.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    ss.setSpan(AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
               0, ss.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    title = ss
    setContentView(R.layout.activity_setup)
    val i = intent
    (findViewById<View>(R.id.inputIP) as EditText).setText(i.getStringExtra("ipAddress"))
    (findViewById<View>(R.id.inputPort) as EditText).setText(i.getIntExtra("port", 0).toString())
  }


  @Suppress("UNUSED_PARAMETER")
  fun okClicked(view: View) {
    val result = Intent()
    result.putExtra("ipAddress",
                    (findViewById<View>(R.id.inputIP) as EditText).text.toString())
    result.putExtra("port",
                    Integer.valueOf((findViewById<View>(R.id.inputPort) as EditText)
                                    .text.toString()))
    setResult(RESULT_OK, result)
    finish()
  }
}
