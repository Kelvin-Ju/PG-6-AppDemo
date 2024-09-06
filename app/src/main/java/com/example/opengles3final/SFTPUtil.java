package com.example.opengles3final;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.util.List;

public class SFTPUtil {
    private static final String SERVER_HOST = ServerConfig.SERVER_HOST;
    private static final int SERVER_PORT = ServerConfig.SERVER_PORT;
    private static final String SERVER_USERNAME = ServerConfig.SERVER_USERNAME;
    private static final String SERVER_PASSWORD = ServerConfig.SERVER_PASSWORD;

    public static void main(String[] args) {
        SFTPUtil sftpUtil = new SFTPUtil();
        sftpUtil.deleteDirectory(ServerConfig.SERVER_USER_DIRECTORY + "/sub1");
    }

    public void deleteDirectory(String directoryPath) {
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
            deleteRemoteDirectory(channelSftp, directoryPath);
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
    }

    private void deleteRemoteDirectory(ChannelSftp channelSftp, String directoryPath) throws SftpException {
        try {
            List<ChannelSftp.LsEntry> files = channelSftp.ls(directoryPath);
            for (ChannelSftp.LsEntry entry : files) {
                String filePath = directoryPath + "/" + entry.getFilename();
                if (!entry.getAttrs().isDir()) {
                    channelSftp.rm(filePath);
                } else if (!entry.getFilename().equals(".") && !entry.getFilename().equals("..")) {
                    deleteRemoteDirectory(channelSftp, filePath);
                }
            }
            channelSftp.rmdir(directoryPath);
        } catch (SftpException e) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                // Directory does not exist, no action needed
            } else {
                throw e;
            }
        }
    }
}

