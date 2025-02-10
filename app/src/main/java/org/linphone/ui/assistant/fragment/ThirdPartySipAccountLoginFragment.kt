/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.assistant.fragment

import android.content.Context
import android.os.Bundle
import android.telephony.TelephonyManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.annotation.UiThread
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AssistantThirdPartySipAccountLoginFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.GenericFragment
import org.linphone.ui.assistant.viewmodel.ThirdPartySipAccountLoginViewModel
import org.linphone.utils.PhoneNumberUtils
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@UiThread
class ThirdPartySipAccountLoginFragment : GenericFragment() {
    companion object {
        private const val TAG = "[Third Party SIP Account Login Fragment]"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS
    )

    private lateinit var binding: AssistantThirdPartySipAccountLoginFragmentBinding

    private val viewModel: ThirdPartySipAccountLoginViewModel by navGraphViewModels(
        R.id.assistant_nav_graph
    )

    private val dropdownListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            val transport = viewModel.availableTransports[position]
            Log.i("$TAG Selected transport updated [$transport]")
            viewModel.transport.value = transport
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AssistantThirdPartySipAccountLoginFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkAndRequestPermissions()

        binding.lifecycleOwner = viewLifecycleOwner

        adapter = ArrayAdapter(
            requireContext(),
            R.layout.drop_down_item,
            viewModel.availableTransports
        )
        adapter.setDropDownViewResource(R.layout.generic_dropdown_cell)
        binding.transport.adapter = adapter
        binding.transport.onItemSelectedListener = dropdownListener

        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        binding.setBackClickListener {
            goBack()
        }

        viewModel.showPassword.observe(viewLifecycleOwner) {
            lifecycleScope.launch {
                delay(50)
                binding.password.setSelection(binding.password.text?.length ?: 0)
            }
        }

        viewModel.accountLoggedInEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Account successfully logged-in")
                (requireActivity() as GenericActivity).showGreenToast(
                    "Registration Successful",
                    R.drawable.ear
                )
            }
        }

        viewModel.accountDeletedEvent.observe(viewLifecycleOwner) {
            it.consume {
                Log.i("$TAG Account successfully unregistered")
                (requireActivity() as GenericActivity).showGreenToast(
                    "Unregistration Successful",
                    R.drawable.check
                )
            }
        }

        viewModel.accountLoginErrorEvent.observe(viewLifecycleOwner) {
            it.consume { message ->
                (requireActivity() as GenericActivity).showRedToast(
                    message,
                    R.drawable.warning_circle
                )
            }
        }

        viewModel.defaultTransportIndexEvent.observe(viewLifecycleOwner) {
            it.consume { index ->
                binding.transport.setSelection(index)
            }
        }

//        coreContext.bearerAuthenticationRequestedEvent.observe(viewLifecycleOwner) {
//            it.consume { pair ->
//                val serverUrl = pair.first
//                val username = pair.second
//
//                Log.i(
//                    "$TAG Navigating to Single Sign On Fragment with server URL [$serverUrl] and username [$username]"
//                )
//                if (findNavController().currentDestination?.id == R.id.thirdPartySipAccountLoginFragment) {
//                    val action = SingleSignOnFragmentDirections.actionGlobalSingleSignOnFragment(
//                        serverUrl,
//                        username
//                    )
//                    findNavController().navigate(action)
//                }
//            }
//        }

        val telephonyManager = requireContext().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val countryIso = telephonyManager.networkCountryIso
        coreContext.postOnCoreThread {
            val dialPlan = PhoneNumberUtils.getDeviceDialPlan(countryIso)
            if (dialPlan != null) {
                viewModel.internationalPrefix.postValue(dialPlan.countryCallingCode)
                viewModel.internationalPrefixIsoCountryCode.postValue(dialPlan.isoCountryCode)
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(requireActivity(), missingPermissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = permissions.filterIndexed { index, _ -> grantResults[index] != PackageManager.PERMISSION_GRANTED }

            if (deniedPermissions.isNotEmpty()) {
                // Handle denied permissions (e.g., show a dialog explaining why they're needed)
                Log.e(TAG, "Permissions denied: $deniedPermissions")
            }
        }
    }

    private fun goBack() {
        findNavController().popBackStack()
    }
}
