package com.example.opengles3final;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;

import java.util.Vector;

public class SftpHelper {

    private static final String SERVER_HOST = "172.21.89.163";
    private static final int SERVER_PORT = 33824;
    private static final String SERVER_USERNAME = "sann0002";
    private static final String SERVER_PASSWORD = "Your password";

    public static Vector<ChannelSftp.LsEntry> listDirectories(String remoteDir) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelSftp channelSftp = null;
        Vector<ChannelSftp.LsEntry> filesList = null;
        try {
            session = jsch.getSession(SERVER_USERNAME, SERVER_HOST, SERVER_PORT);
            session.setPassword(SERVER_PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();

            filesList = channelSftp.ls(remoteDir);

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
        return filesList;
    }

    public static Vector<ChannelSftp.LsEntry> listFiles(String remoteDir) {
        return listDirectories(remoteDir);
    }
}

