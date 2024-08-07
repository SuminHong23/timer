package com.hong.timetostretch

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.fragment.app.DialogFragment
import com.hong.timetostretch.databinding.CustomBreakTimePickerBinding

class BreakTimePickerDialogFragment : DialogFragment() {
    private var _binding: CustomBreakTimePickerBinding? = null
    private val binding get() = _binding!!

    var listener: ((Int, Int) -> Unit)? = null
    var defaultMinutes: Int = 0
    var defaultSeconds: Int = 0

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = CustomBreakTimePickerBinding.inflate(LayoutInflater.from(context))

        // Set default values for minutes and seconds
        binding.editTextMinutes.setText(defaultMinutes.toString())
        binding.editTextSeconds.setText(defaultSeconds.toString())

        return AlertDialog.Builder(requireContext()).apply {
            setView(binding.root)
            setPositiveButton("Set") { dialog, which ->
                val minutes = binding.editTextMinutes.text.toString().toIntOrNull() ?: defaultMinutes
                val seconds = binding.editTextSeconds.text.toString().toIntOrNull() ?: defaultSeconds
                listener?.invoke(minutes, seconds)
            }
            setNegativeButton("Cancel", null)
        }.create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}