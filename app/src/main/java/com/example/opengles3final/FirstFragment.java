package com.example.opengles3final;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.example.opengles3final.databinding.FragmentFirstBinding;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.util.ArrayList;
import java.util.List;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;
    private static final String SERVER_HOST = ServerConfig.SERVER_HOST;
    private static final int SERVER_PORT = ServerConfig.SERVER_PORT;
    private static final String SERVER_USERNAME = ServerConfig.SERVER_USERNAME;
    private static final String SERVER_PASSWORD = ServerConfig.SERVER_PASSWORD;

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
        new FetchSubjectIDsTask().execute();

        // Set up the click listener for the ListView items to open the Options fragment with different models
        listView.setOnItemClickListener((parent, itemView, position, id) -> {
            String selectedSubjectId = (String) parent.getItemAtPosition(position);
            openOptionsFragment(selectedSubjectId);
        });
    }

    private void openOptionsFragment(String subjectId) {
        Options optionsFragment = Options.newInstance(subjectId);

        // Use the FragmentManager to replace the current fragment with the Options fragment
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.main_fragment_container, optionsFragment)
                    .addToBackStack(null) // Optional, for navigation back to the previous fragment
                    .commit();
        }
    }

    private class FetchSubjectIDsTask extends AsyncTask<Void, Void, List<String>> {
        @Override
        protected List<String> doInBackground(Void... voids) {
            List<String> subjectIds = new ArrayList<>();
            JSch jsch = new JSch();
            Session session = null;
            ChannelSftp channelSftp = null;

            try {
                session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
                session.setPassword(SERVER_PASSWORD);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                // Navigate to the directory containing subject IDs
                channelSftp.cd(ServerConfig.SERVER_USER_DIRECTORY);

                List<ChannelSftp.LsEntry> files = channelSftp.ls(".");
                for (ChannelSftp.LsEntry entry : files) {
                    String fileName = entry.getFilename();
                    if (entry.getAttrs().isDir() && !fileName.equals(".") && !fileName.equals("..") && !fileName.startsWith(".")) {
                        subjectIds.add(fileName);
                    }
                }

            } catch (JSchException | SftpException e) {
                e.printStackTrace();
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            return subjectIds;
        }

        @Override
        protected void onPostExecute(List<String> subjectIds) {
            if (getActivity() != null && !subjectIds.isEmpty()) {
                ListView listView = binding.listview;
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_list_item_1, subjectIds);
                listView.setAdapter(adapter);
            }
        }
    }
}
