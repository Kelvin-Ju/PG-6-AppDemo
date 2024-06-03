package com.example.opengles3final;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.opengles3final.databinding.FragmentFirstBinding;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Assuming your FragmentFirstBinding has a ListView with the ID listview
        ListView listView = binding.listview;
        String[] items = new String[]{"Wrinkle+Scar", "Wrinkle+Scar+NoEyeMask", "Wrinkle", "Acne", "Clear", "Model 5"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);

        // Set up the click listener for the ListView items to open the Options fragment with different models
        listView.setOnItemClickListener((parent, itemView, position, id) -> {
            openOptionsFragment(position);
        });
    }

    private void openOptionsFragment(int modelIndex) {
        String modelUri;
        switch (modelIndex) {
            case 0:
                modelUri = "android://com.example.opengles3final/assets/models/003F_0_hrn_mid_mesh.obj";
                break;
            case 1:
                modelUri = "android://com.example.opengles3final/assets/models/003F_0_hrn_mid_meshF.obj";
                break;
            case 2:
                modelUri = "android://com.example.opengles3final/assets/models/004F_0_hrn_mid_mesh.obj";
                break;
            case 3:
                modelUri = "android://com.example.opengles3final/assets/models/013_30D_F_0_hrn_mid_mesh.obj";
                break;
            case 4:
                modelUri = "android://com.example.opengles3final/assets/models/007_30D_F_0_hrn_mid_mesh.obj";
                break;
            case 5:
                modelUri = "android://com.example.opengles3final/assets/models/006_30D_F_0_hrn_mid_mesh.obj";
                break;
            default:
                modelUri = "android://com.example.opengles3final/assets/models/default.obj";
                break;
        }

        Options optionsFragment = Options.newInstance(modelUri);

        // Use the FragmentManager to replace the current fragment with the Options fragment
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_fragment_container, optionsFragment)
                    .addToBackStack(null) // Optional, for navigation back to the previous fragment
                    .commit();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
