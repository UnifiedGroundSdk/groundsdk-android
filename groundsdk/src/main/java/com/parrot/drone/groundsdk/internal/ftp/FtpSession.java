package com.parrot.drone.groundsdk.internal.ftp;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.parrot.drone.groundsdk.internal.Cancelable;
import com.parrot.drone.groundsdk.internal.ftp.apachecommons.CopyStreamAdapter;
import com.parrot.drone.groundsdk.internal.ftp.apachecommons.FTPClient;
import com.parrot.drone.groundsdk.internal.ftp.apachecommons.FTPFile;
import com.parrot.drone.groundsdk.internal.ftp.apachecommons.FTPReply;
import com.parrot.drone.sdkcore.arsdk.device.ArsdkTcpProxy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.net.SocketFactory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FtpSession {
    private final String CLASS_NAME = this.getClass().getSimpleName();

    private Cancelable cancelable = () -> { /* do nothing */ };

    public interface CreateProxyListener {
        void onCreateProxy(final int port, ArsdkTcpProxy.Listener proxyCreatedCallback);
    }
    public interface RemoveProxyListener {
        void onRemoveProxy(final int port);
    }

    public interface FtpTransferListener {
        void onTransferCompleted(final boolean successful, @Nullable Object data);
        void onTransferProgress(final int percent);
    }
    public interface FtpListingListener {
        void onListingCompleted(final boolean successful, @Nullable SortedMap<String, FTPFile> files);
    }

    private final String address;
    private final int port;
    private final SocketFactory socketFactory;

    private final CreateProxyListener createProxyListener;
    private final RemoveProxyListener removeProxyListener;

    public FtpSession(@NonNull final String address,
                      final int port,
                      @Nullable final SocketFactory socketFactory) { this(address, port, socketFactory, null, null); }
    public FtpSession(@NonNull final String address, 
                      final int port, 
                      @Nullable final SocketFactory socketFactory,
                      @Nullable CreateProxyListener createProxyListener,
                      @Nullable RemoveProxyListener removeProxyListener) {

        this.address = address;
        this.port = port;
        this.socketFactory = socketFactory;

        this.createProxyListener = createProxyListener;
        this.removeProxyListener = removeProxyListener;
    }


    public Cancelable cancelableStoreFile(@NonNull final File localFile, @NonNull final String remoteFile, @Nullable final FtpTransferListener listener) {
        try {
            storeFile(new FileInputStream(localFile), remoteFile, listener);
            return cancelable;
        } catch (FileNotFoundException e) {
            return null;
        }
    }
    public boolean storeFile(@NonNull final File localFile, @NonNull final String remoteFile, @Nullable final FtpTransferListener listener) {
        try {
            storeFile(new FileInputStream(localFile), remoteFile, listener);
            return true;
        } catch (FileNotFoundException e) {
            return false;
        }
    }
    public void storeFile(@NonNull final InputStream is, @NonNull final String remoteFile, @Nullable final FtpTransferListener listener) {
        final Thread storeFileThread = new Thread() {
            @Override
            public void run() {
                final FTPClient client = FtpSession.this.createFtpClient();

                try {
                    FtpSession.this.connect(client);

                    if (!client.storeFile(remoteFile, is)) {
                        throw new Exception("store failed for " + remoteFile);
                    }

                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(true, remoteFile));
                    }

                } catch (Exception ex) {
                    Log.w(CLASS_NAME, "Unable to FTP STORE file: " + ex.getMessage(), ex);
                    if (listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(false, null));
                    }

                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        // do nothing
                    }

                    if (client.isConnected()) {
                        try {
                            client.disconnect();
                        } catch (IOException e) {
                            // do nothing
                        }
                    }
                }
            }
        };

        storeFileThread.start();
    }

    public void getFileList(@Nullable final FtpListingListener listener) {
        new Thread(() -> {
            final FTPClient client = createFtpClient();
            final SortedMap<String, FTPFile> remoteFiles = new TreeMap<>();

            try {
                connect(client);
                listFiles(client, remoteFiles);

                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onListingCompleted(true, remoteFiles));
                }

            } catch (Exception ex) {
                Log.w(CLASS_NAME, "Unable to FTP LIST files: " + ex.getMessage(), ex);
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onListingCompleted(false, null));
                }

            } finally {
                if (client.isConnected()) {
                    try {
                        client.disconnect();
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }

        }).start();
    }
    private void listFiles(@NonNull FTPClient client, @NonNull final SortedMap<String, FTPFile> remoteFiles) throws IOException { listFiles(client, remoteFiles, null); }
    private void listFiles(@NonNull FTPClient client, @NonNull final SortedMap<String, FTPFile> remoteFiles, @Nullable String path) throws IOException {
        final FTPFile[] files;

        if (path == null || path.length() == 0) {
            path = "";
            files = client.listFiles();
        } else {
            files = client.listFiles(path);
        }

        for (FTPFile file : files) {
            if (file.isDirectory()) {
                listFiles(client, remoteFiles, path + File.separator + file.getName());
            } else {
                remoteFiles.put(path + File.separator + file.getName(), file);
            }
        }
    }

    public void retrieveFile(@NonNull final String remoteFile, @NonNull final String localFile, final boolean deleteRemote, @Nullable final FtpTransferListener listener) {
        new Thread(() -> {
            final FTPClient client = createFtpClient();
            FileOutputStream fos = null;

            try {
                // not sure if this is necessary
                client.setBufferSize(1048576);

                connect(client);

                if (listener != null) {
                    final FTPFile[] files = client.listFiles(remoteFile);

                    if (files == null || files.length == 0) {
                        throw new Exception("remote file not found: " + remoteFile);
                    }

                    final CopyStreamAdapter streamListener = new CopyStreamAdapter() {
                        @Override
                        public void bytesTransferred(long totalBytesTransferred, int bytesTransferred, long streamSize) {
                            int percent = (int) (totalBytesTransferred * 100 / files[0].getSize());
                            new Handler(Looper.getMainLooper()).post(() -> listener.onTransferProgress(percent));
                        }
                    };

                    client.setCopyStreamListener(streamListener);
                }

                fos = new FileOutputStream(new File(localFile));
                
                if (!client.retrieveFile(remoteFile, fos)) {
                    throw new Exception("retrieve failed for " + remoteFile);
                }

                fos.flush();

                if (deleteRemote) {
                    if (!client.deleteFile(remoteFile)) {
                    throw new Exception("delete failed for " + remoteFile);
                    }
                }

                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(true, localFile));
                }

            } catch (Exception ex) {
                Log.w(CLASS_NAME, "Unable to FTP RETRIEVE file: " + ex.getMessage(), ex);
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(false, null));
                }
                
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        // do nothing
                    }
                }
                
                if (client.isConnected()) {
                    try {
                        client.disconnect();
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }

        }).start();
    }

    public void retrieveFileContents(@NonNull final String remoteFile, @NonNull final FtpTransferListener listener) {
        new Thread(() -> {
            final FTPClient client = createFtpClient();
            ByteArrayOutputStream baos = null;

            try {
                // not sure if this is necessary
                client.setBufferSize(1048576);

                connect(client);
                baos = new ByteArrayOutputStream();

                if (!client.retrieveFile(remoteFile, baos)) {
                    throw new Exception("retrieve failed for " + remoteFile);
                }

                final byte[] contents = baos.toByteArray();
                new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(true, contents));

            } catch (Exception ex) {
                Log.w(CLASS_NAME, "Unable to FTP RETRIEVE file contents: " + ex.getMessage(), ex);
                new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(false, null));

            } finally {
                if (baos != null) {
                    try {
                        baos.close();
                    } catch (IOException e) {
                        // do nothing
                    }
                }

                if (client.isConnected()) {
                    try {
                        client.disconnect();
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }
        }).start();
    }

    public void deleteFile(@NonNull final String remoteFile, @Nullable final FtpTransferListener listener) {
        new Thread(() -> {
            final FTPClient client = createFtpClient();
            final SortedMap<String, FTPFile> remoteFiles = new TreeMap<>();

            try {
                connect(client);

                if (!client.deleteFile(remoteFile) && listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(true, remoteFiles));
                }

            } catch (Exception ex) {
                Log.w(CLASS_NAME, "Unable to FTP LIST files: " + ex.getMessage(), ex);
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(false, null));
                }

            } finally {
                if (client.isConnected()) {
                    try {
                        client.disconnect();
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }

        }).start();
    }

    public void fullWipe(@Nullable final FtpTransferListener listener) {
        new Thread(() -> {
            final FTPClient client = createFtpClient();
            final SortedMap<String, FTPFile> remoteFiles = new TreeMap<>();

            try {
                connect(client);
                listFiles(client, remoteFiles);

//                long count = 0;

                for (String remoteFile : remoteFiles.keySet()) {
                    if (!client.deleteFile(remoteFile) && listener != null) {
                        new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(false, remoteFiles));
                        return;
//                    } else if (listener != null) {
//                        final int percent = Math.round(++count / remoteFiles.size() * 100);
//                        new Handler(Looper.getMainLooper()).post(() -> listener.onTransferProgress(percent));
                    }
                }

                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(true, remoteFiles));
                }

            } catch (Exception ex) {
                Log.w(CLASS_NAME, "Unable to FTP LIST files: " + ex.getMessage(), ex);
                if (listener != null) {
                    new Handler(Looper.getMainLooper()).post(() -> listener.onTransferCompleted(false, null));
                }

            } finally {
                if (client.isConnected()) {
                    try {
                        client.disconnect();
                    } catch (IOException e) {
                        // do nothing
                    }
                }
            }
        }).start();
    }

    private FTPClient createFtpClient() {
        if (createProxyListener == null || removeProxyListener == null) {
            return new FTPClient();
        } else {
            return new FTPClient(this::createProxy, this::removeProxy);
        }
    }

    private void connect(@NonNull final FTPClient client) throws Exception {
        if (socketFactory != null) {
            client.setSocketFactory(socketFactory);
        }

        client.setDefaultTimeout(5000);
        client.setConnectTimeout(5000);
        client.setDataTimeout(5000);

        client.connect(address, port);
        final int reply = client.getReplyCode();

        if (!FTPReply.isPositiveCompletion(reply)) {
            client.disconnect();
            throw new Exception("connect failed - code " + reply);
        }

        if (!client.login("anonymous", "groundsdk@parrot.com")) {
            client.disconnect();
            throw new Exception("login failed");
        }

        client.enterLocalPassiveMode();
        client.setFileType(FTPClient.BINARY_FILE_TYPE);
        client.setFileTransferMode(FTPClient.BINARY_FILE_TYPE);
    }

    // proxy chain
    private void createProxy(final int port, ArsdkTcpProxy.Listener proxyCreatedCallback ) {
        if (createProxyListener != null) {
            createProxyListener.onCreateProxy(port, proxyCreatedCallback);
        }
    }

    private void removeProxy(final int port) {
        if (port != -1 && removeProxyListener != null) {
            removeProxyListener.onRemoveProxy(port);
        }
    }
}
