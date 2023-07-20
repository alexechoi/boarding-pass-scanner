package com.alexchoi.boardingpassscanner

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class ResultsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)

        val logoImageView: ImageView = findViewById(R.id.logoImageView)
        val titleTextView: TextView = findViewById(R.id.titleTextView)
        val resultsTextView: TextView = findViewById(R.id.resultsTextView)
        val backButton: Button = findViewById(R.id.backButton)

        logoImageView.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_launcher_foreground))
        titleTextView.text = getString(R.string.title_boarding_pass_information)
        resultsTextView.text = intent.getStringExtra("scannedInfo")

        backButton.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
    }
}