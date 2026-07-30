package com.example.voiceassistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    companion object {
        const val RECORD_AUDIO_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("crash_log", MODE_PRIVATE)
        val lastCrash = prefs.getString("last_crash", null)
        if (lastCrash != null) {
            val scrollView = ScrollView(this)
            val tv = TextView(this)
            tv.text = "শেষবার অ্যাপ বন্ধ হওয়ার কারণ:\n\n$lastCrash"
            tv.setPadding(24, 24, 24, 24)
            tv.textSize = 12f
            tv.setTextIsSelectable(true)
            scrollView.addView(tv)
            setContentView(scrollView)
            prefs.edit().remove("last_crash").apply()
            return
        }

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            getSharedPreferences("crash_log", MODE_PRIVATE).edit()
                .putString("last_crash", sw.toString()).apply()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        resultText = findViewById(R.id.resultText)
        val micButton = findViewById<Button>(R.id.micButton)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_REQUEST_CODE
            )
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        micButton.setOnClickListener {
            startListening()
        }
    }

    private fun startListening() {
        statusText.text = "শুনছি..."

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "bn-BD")
        }

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.get(0) ?: ""
                resultText.text = "তুমি বললে: $spokenText"
                handleCommand(spokenText)
            }

            override fun onError(error: Int) {
                statusText.text = "মাইকে আবার ট্যাপ করো"
            }

            override fun onReadyForSpeech(params: Bundle?) { statusText.text = "বলো..." }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { statusText.text = "প্রসেস হচ্ছে..." }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        speechRecognizer.startListening(intent)
    }

    private fun handleCommand(text: String) {
        val command = text.trim().lowercase()

        when {
            command.contains("ইউটিউব") -> {
                openApp("com.google.android.youtube", "https://youtube.com")
                statusText.text = "ইউটিউব খুলছি..."
            }

            command.contains("ফেসবুক") -> {
                openApp("com.facebook.katana", "https://facebook.com")
                statusText.text = "ফেসবুক খুলছি..."
            }

            command.contains("হোয়াটসঅ্যাপ") || command.contains("হোয়াটস অ্যাপ") -> {
                openApp("com.whatsapp", null)
                statusText.text = "হোয়াটসঅ্যাপ খুলছি..."
            }

            command.contains("ছবি পাঠা") -> {
                shareImage()
                statusText.text = "ছবি বেছে নাও এবং কাকে পাঠাবে সিলেক্ট করো"
            }

            command.contains("কল কর") -> {
                val dialIntent = Intent(Intent.ACTION_DIAL)
                startActivity(dialIntent)
                statusText.text = "ডায়ালার খুলছি..."
            }

            else -> {
                val searchIntent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                    putExtra(android.app.SearchManager.QUERY, command)
                }
                try {
                    startActivity(searchIntent)
                    statusText.text = "সার্চ করছি: $command"
                } catch (e: Exception) {
                    Toast.makeText(this, "এই কমান্ডটা এখনো যোগ করা হয়নি", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openApp(packageName: String, fallbackUrl: String?) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else if (fallbackUrl != null) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)))
        } else {
            Toast.makeText(this, "অ্যাপটি ইনস্টল করা নেই", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareImage() {
        val pickIntent = Intent(Intent.ACTION_PICK)
        pickIntent.type = "image/*"
        startActivityForResult(pickIntent, 200)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && data != null) {
            val imageUri: Uri? = data.data
            if (imageUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/*"
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "কাকে পাঠাবে?"))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
    }
}
