package com.example.opengles3final;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.example.opengles3final.databinding.ActivityMainBinding;
import android.view.Menu;
import android.view.MenuItem;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import android.text.InputType;
import android.widget.Toast;
import java.net.URL;
import com.jcraft.jsch.*;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private String serverHost;
    private int serverPort;
    private String serverUsername;
    private String serverPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        URL.setURLStreamHandlerFactory(new AndroidURLStreamHandlerFactory());

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);

        // Load configuration
        Config config = new Config(this);
        serverHost = config.getProperty("server_host");
        serverPort = Integer.parseInt(config.getProperty("server_port"));
        serverUsername = config.getProperty("server_username");
        serverPassword = config.getProperty("server_password");

        // Load the first fragment at start
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.main_fragment_container, new FirstFragment())
                    .commit();
        }

        binding.fab.setOnClickListener(view -> {
            // Check for camera permission
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            } else {
                requestSubjectIDAndOpenFragment();
            }
        });
    }

    private void requestSubjectIDAndOpenFragment() {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Enter Subject ID");

        final EditText input = new EditText(MainActivity.this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String subjectID = input.getText().toString();
            checkIfSubjectIDExists(subjectID, () -> openButtonFragmentWithSubjectID(subjectID));
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void checkIfSubjectIDExists(String subjectID, Runnable onSuccess) {
        Thread thread = new Thread(() -> {
            JSch jsch = new JSch();
            Session session = null;
            ChannelSftp channelSftp = null;
            boolean exists = false;
            try {
                session = jsch.getSession(serverUsername, serverHost, serverPort);
                session.setPassword(serverPassword);
                session.setConfig("StrictHostKeyChecking", "no");
                session.connect();

                channelSftp = (ChannelSftp) session.openChannel("sftp");
                channelSftp.connect();

                String remoteSubjectDirectory = ServerConfig.SERVER_USER_DIRECTORY + "/" + subjectID;
                exists = directoryExists(channelSftp, remoteSubjectDirectory);

            } catch (JSchException e) {
                Log.e("SFTP", "Failed to check if directory exists: " + e.getMessage(), e);
            } finally {
                if (channelSftp != null && channelSftp.isConnected()) {
                    channelSftp.disconnect();
                }
                if (session != null && session.isConnected()) {
                    session.disconnect();
                }
            }

            boolean finalExists = exists;
            runOnUiThread(() -> {
                if (finalExists) {
                    Toast.makeText(MainActivity.this, "Subject ID already exists on the server", Toast.LENGTH_LONG).show();
                } else {
                    onSuccess.run();
                }
            });
        });
        thread.start();
    }

    private boolean directoryExists(ChannelSftp channelSftp, String path) {
        try {
            SftpATTRS attrs = channelSftp.lstat(path);
            return attrs.isDir();
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            } else {
                Log.e("SFTP", "Failed to check directory existence: " + e.getMessage(), e);
                return false;
            }
        }
    }

    private void openButtonFragmentWithSubjectID(String subjectID) {
        ButtonFragment buttonFragment = new ButtonFragment();
        Bundle args = new Bundle();
        args.putString("SUBJECT_ID", subjectID); // Key to retrieve in the fragment
        buttonFragment.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.main_fragment_container, buttonFragment); // Ensure this is your container ID
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void openSecondFragment() {
        SecondFragment secondFragment = new SecondFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.main_fragment_container, secondFragment)
                .addToBackStack(null) // Optional, if you want to support back navigation
                .commit();
    }

    private void openCameraFragment() {
        CameraFragment cameraFragment = new CameraFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.main_fragment_container, cameraFragment); // Ensure you have a FrameLayout or similar in your activity_main.xml as the container
        transaction.addToBackStack(null);
        transaction.commit();
    }

    private void openButtonFragment() {
        ButtonFragment buttonFragment = new ButtonFragment();
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.main_fragment_container, buttonFragment); // Use the correct container ID where you want to place your fragment
        transaction.addToBackStack(null); // Add to back stack for navigation
        transaction.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }
}
