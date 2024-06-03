package com.example.opengles3final;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import androidx.fragment.app.Fragment;

public class Options extends Fragment {

    private static final String ARG_MODEL_URI = "model_uri";
    private String modelUri;

    public Options() {
        // Required empty public constructor
    }

    public static Options newInstance(String modelUri) {
        Options fragment = new Options();
        Bundle args = new Bundle();
        args.putString(ARG_MODEL_URI, modelUri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            modelUri = getArguments().getString(ARG_MODEL_URI);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_options, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Find the "Model" button by its ID and set a click listener
        Button modelButton = view.findViewById(R.id.model);
        modelButton.setOnClickListener(v -> openSecondFragment());

        Button nerfButton = view.findViewById(R.id.nerf);
        nerfButton.setOnClickListener(v -> openNerfFragment());
    }

    private void openNerfFragment() {
        NerfFragment nerfFragment = new NerfFragment();
        // Optionally, pass arguments to the second fragment
        Bundle args = new Bundle();
        args.putString("someKey", "someValue");
        nerfFragment.setArguments(args);

        // Use the FragmentManager to replace the current fragment with the NerfFragment
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_fragment_container, nerfFragment)
                    .addToBackStack(null) // Optional, for navigation back to the previous fragment
                    .commit();
        }
    }

    private void openSecondFragment() {
        SecondFragment secondFragment = SecondFragment.newInstance(modelUri);

        // Use the FragmentManager to replace the current fragment with the SecondFragment
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_fragment_container, secondFragment)
                    .addToBackStack(null) // Optional, for navigation back to the previous fragment
                    .commit();
        }
    }
}
