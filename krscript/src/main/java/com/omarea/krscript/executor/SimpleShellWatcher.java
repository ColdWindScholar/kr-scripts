package com.omarea.krscript.executor;

import android.content.Context;

import com.omarea.common.shell.ShellTranslation;
import com.omarea.krscript.model.ShellHandlerBase;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class SimpleShellWatcher {

    /**
     * 设置日志处理Handler
     *
     * @param process          Runtime进程
     * @param shellHandlerBase ShellHandlerBase
     */
    public void setHandler(Context context, Process process, final ShellHandlerBase shellHandlerBase, final Runnable onExit) {
        final ShellTranslation shellTranslation = new ShellTranslation(context);

        final InputStream inputStream = process.getInputStream();
        final InputStream errorStream = process.getErrorStream();
        final Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                String line;
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    while ((line = bufferedReader.readLine()) != null) {
                        shellHandlerBase.sendMessage(
                            shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_REDE, shellTranslation.resolveRow(line) + "\n")
                        );
                    }
                } catch (Exception ignored) {
                }
            }
        });
        final Thread readerError = new Thread(new Runnable() {
            @Override
            public void run() {
                String line;
                try {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
                    while ((line = bufferedReader.readLine()) != null) {
                        shellHandlerBase.sendMessage(
                            shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_READ_ERROR, shellTranslation.resolveRow(line) + "\n")
                        );
                    }
                } catch (Exception ignored) {
                }
            }
        });
        final Process processFinal = process;
        Thread waitExit = new Thread(new Runnable() {
            @Override
            public void run() {
                int status = -1;
                try {
                    status = processFinal.waitFor();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    shellHandlerBase.sendMessage(shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_EXIT, status));
                    if (reader.isAlive()) {
                        reader.interrupt();
                    }
                    if (readerError.isAlive()) {
                        readerError.interrupt();
                    }
                    if (onExit != null) {
                        onExit.run();
                    }
                }
            }
        });

        reader.start();
        readerError.start();
        waitExit.start();
    }
}
