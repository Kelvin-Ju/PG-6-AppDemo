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
        ListView listView = binding.listview; // Direct access via binding if available
        String[] items = new String[]{"Item 1", "Item 2", "Item 3", "Item 4"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, items);
        listView.setAdapter(adapter);
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
