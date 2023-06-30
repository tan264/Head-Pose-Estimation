package com.example.facedetection.fragments

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.facedetection.R
import com.example.facedetection.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val activityResultLancher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(
                    context,
                    getString(R.string.permission_request_granted),
                    Toast.LENGTH_SHORT
                ).show()
                findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToCameraFragment())
            } else {
                Toast.makeText(
                    context,
                    getString(R.string.permission_request_denied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Log.d("tan264", "a")
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        binding.apply {
            lifecycleOwner = this@HomeFragment
        }
        Log.d("tan264", _binding.toString())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            buttonCheck.setOnClickListener {
                navigateCameraFragment()
            }
            imageFace.setOnClickListener {
                navigateCameraFragment()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun navigateCameraFragment() {
        if (hasCameraPermission()) {
            findNavController().navigate(HomeFragmentDirections.actionHomeFragmentToCameraFragment())
        } else {
            activityResultLancher.launch(android.Manifest.permission.CAMERA)
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        requireContext(),
        android.Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}