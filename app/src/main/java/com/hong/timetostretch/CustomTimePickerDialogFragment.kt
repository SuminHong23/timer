package com.hong.timetostretch

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.hong.timetostretch.databinding.CustomTimePickerBinding

class CustomTimePickerDialogFragment : DialogFragment() {
    private var _binding: CustomTimePickerBinding? = null
    private val binding get() = _binding!!

    var listener: ((Int, Int) -> Unit)? = null
    var defaultHour: Int = 0  // Default hour set to 0
    var defaultMinute: Int = 0  // Default minute set to 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = CustomTimePickerBinding.inflate(LayoutInflater.from(context))

        // Set the default values for hour and minute
        binding.editTextHour.setText(defaultHour.toString())
        binding.editTextMinute.setText(defaultMinute.toString())

        return AlertDialog.Builder(requireContext()).apply {
            setView(binding.root)
            setPositiveButton("Set") { dialog, which ->
                val hour = binding.editTextHour.text.toString().toIntOrNull() ?: defaultHour
                val minute = binding.editTextMinute.text.toString().toIntOrNull() ?: defaultMinute
                listener?.invoke(hour, minute)
            }
            setNegativeButton("Cancel", null)
        }.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}