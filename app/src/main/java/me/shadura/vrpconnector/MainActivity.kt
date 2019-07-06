package me.shadura.vrpconnector

/* Copyright (C) 2019 Andrej Shadura */
/* SPDX-License-Identifier: MIT */

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import java.math.BigDecimal
import java.math.RoundingMode

fun Context.installApp(packageName: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.Builder().run {
            scheme("market")
            authority("details")
            appendQueryParameter("id", packageName)
            build()
        }))
    } catch (e: ActivityNotFoundException) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.Builder().run {
            scheme("https")
            authority("play.google.com")
            path("store/apps/details")
            appendQueryParameter("id", packageName)
            build()
        }))
    }
}

object VrpUriBuilder {
    private fun builder(): Uri.Builder =
        Uri.Builder().run {
            scheme("sk.financnasprava.vrp.app")
            authority("response")
        }

    fun success(transactionId: String) =
            builder().apply {
                appendQueryParameter("state", "APPROVED")
                appendQueryParameter("transactionId", transactionId)
            }

    fun failure() =
            builder().apply {
                appendQueryParameter("state", "ERROR")
            }

    fun declined() =
        builder().apply {
            appendQueryParameter("state", "DECLINED")
        }
}

class MainActivity : AppCompatActivity() {

    class SumUpNotFoundDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            return activity?.run {
                AlertDialog.Builder(this).run {
                    setMessage(getString(R.string.no_sumup))
                    setTitle(getString(R.string.cant_complete_payment))
                    setPositiveButton(getString(R.string.go_back)) { dialog, _ ->
                        dialog.dismiss()

                        val vrpUri = VrpUriBuilder.failure().build()
                        Log.i(TAG, "returning to VRP: $vrpUri")
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, vrpUri))
                        } catch (e: ActivityNotFoundException) {
                            Log.e(TAG, "can’t call VRP")
                        }
                    }
                    setNeutralButton(getString(R.string.install_sumup)) { dialog, _ ->
                        dialog.dismiss()

                        installApp("com.kaching.merchant")
                    }
                    create()
                }
            } ?: throw IllegalStateException("Activity cannot be null")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        intent?.data?.also { uri: Uri ->
            when (intent.action) {
                Intent.ACTION_VIEW -> {
                    when (uri.scheme) {
                        "com.aevi.payment" -> {
                            Log.i(TAG, "$uri")
                            if (uri.authority == "purchase") {
                                val amount = BigDecimal(uri.getQueryParameter("amount") ?: "0")
                                val builder = Uri.Builder().apply {
                                    scheme("sumupmerchant")
                                    authority("pay")
                                    path("1.0")
                                    appendQueryParameter("affiliate-key", BuildConfig.sumupApiKey)
                                    appendQueryParameter("app-id", BuildConfig.APPLICATION_ID)
                                    appendQueryParameter("total", amount.divide(BigDecimal(100), 2, RoundingMode.HALF_EVEN).toPlainString())
                                    appendQueryParameter("currency", "EUR")
                                    appendQueryParameter("callback", "me.shadura.vrpconnector.sumup://result")
                                }
                                val sumupUri = builder.build()
                                Log.i(TAG, "invoking SumUp: $sumupUri")
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, sumupUri))
                                } catch (e: ActivityNotFoundException) {
                                    SumUpNotFoundDialog().show(supportFragmentManager, "NoSumup")
                                }
                            }
                        }
                        "me.shadura.vrpconnector.sumup" -> {
                            Log.i(TAG, "$uri")
                            if (uri.authority == "result") {
                                val vrpUri = when (uri.getQueryParameter("smp-status")) {
                                    "success" ->
                                        VrpUriBuilder.success(uri.getQueryParameter("smp-tx-code") ?: "")
                                    else -> {
                                         when (uri.getQueryParameter("smp-failure-cause")) {
                                            "transaction-failed" ->
                                                VrpUriBuilder.declined()
                                            else ->
                                                VrpUriBuilder.failure()
                                        }
                                    }
                                }.build()

                                Log.i(TAG, "calling back VRP: $vrpUri")
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, vrpUri))
                                } catch (e: ActivityNotFoundException) {
                                    Log.e(TAG, "can’t call VRP")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val TAG = "VRP-SumUp"
    }
}
