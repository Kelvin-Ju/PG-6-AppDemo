package com.example.opengles3final;

import static android.service.controls.ControlsProviderService.TAG;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ButtonFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ButtonFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";
    private String subjectID; // Default ID in case none is provided


    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public ButtonFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_button, container, false);

        Button buttonOpenCamera = view.findViewById(R.id.button_open_camera);
        buttonOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCameraFragment();
            }
        });

        if (getArguments() != null) {
            subjectID = getArguments().getString("SUBJECT_ID", "defaultID");
        }

        Log.d(TAG, "Subject IDBUTT: " + subjectID);

        Button buttonOpenCamera45 = view.findViewById(R.id.button_open_camera_45);
        buttonOpenCamera45.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCameraFragment45();
            }
        });

        Button buttonOpenCameraDiagonals = view.findViewById(R.id.button_open_camera_diagonals);
        buttonOpenCameraDiagonals.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCameraDiagonals();
            }
        });


        return view;
    }

    private void openCameraFragment() {
        CameraFragment cameraFragment = new CameraFragment();
        Bundle args = new Bundle();
        args.putString("SUBJECT_ID", subjectID);
        cameraFragment.setArguments(args);

        // Start CameraFragment with subject ID
        getActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, cameraFragment)
                .addToBackStack(null)
                .commit();
    }

    private void openCameraFragment45() {
        CameraFragment45 cameraFragment45 = new CameraFragment45();
        Bundle args = new Bundle();
        args.putString("SUBJECT_ID", subjectID);
        cameraFragment45.setArguments(args);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, cameraFragment45)
                .addToBackStack(null)
                .commit();
    }


    private void openCameraDiagonals() {
        CameraDiagonals cameraDiagonalsFragment = new CameraDiagonals();
        Bundle args = new Bundle();
        args.putString("SUBJECT_ID", subjectID);
        cameraDiagonalsFragment.setArguments(args);
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, cameraDiagonalsFragment)
                .addToBackStack(null)
                .commit();
    }


}