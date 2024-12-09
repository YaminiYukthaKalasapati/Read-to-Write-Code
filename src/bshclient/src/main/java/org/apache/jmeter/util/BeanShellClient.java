/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

// N.B. Do not call any JMeter methods; the jar is standalone

/**
 * Implements a client that can talk to the JMeter BeanShell server.
 */
public class BeanShellClient {

    private static final int MINARGS = 3;

    public static void main(String[] args) {
        if (args.length < MINARGS) {
            System.out.println("Please provide " + MINARGS + " or more arguments:");
            System.out.println("serverhost serverport filename [arg1 arg2 ...]");
            System.out.println("e.g. ");
            System.out.println("localhost 9000 extras/remote.bsh apple blake 7");
            return;
        }
        String host = args[0];
        String portString = args[1];
        String file = args[2];

        int port = Integer.parseInt(portString) + 1; // convert to telnet port

        System.out.println("Connecting to BSH server on " + host + ":" + portString);

        try (Socket sock = new Socket(host, port);
             InputStream is = sock.getInputStream();
             OutputStream os = sock.getOutputStream()) {

            SockRead sockRead = new SockRead(is);
            sockRead.start();

            sendLine("bsh.prompt=\"\";", os); // Prompt is unnecessary

            sendLine("String [] args={", os);
            for (int i = MINARGS; i < args.length; i++) {
                sendLine("\"" + args[i] + "\",\n", os);
            }
            sendLine("};", os);

            // Efficient file reading and writing using a buffer
            int bufferSize = 8192; // 8 KB buffer
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            try (BufferedReader fis = Files.newBufferedReader(Paths.get(file))) {
                while ((bytesRead = fis.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }

            sendLine("bsh.prompt=\"bsh % \";", os); // Reset for other users
            os.flush();
            sock.shutdownOutput(); // Tell server that we are done
            sockRead.join(); // wait for script to finish
        } catch (ConnectException e) {
            System.err.println("Connection failed to " + host + ":" + port + ". Please check the server.");
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println("I/O error occurred: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void sendLine(String line, OutputStream outPipe) throws IOException {
        outPipe.write(line.getBytes(StandardCharsets.UTF_8));
        outPipe.flush();
    }

    private static class SockRead extends Thread {

        private final InputStream is;

        public SockRead(InputStream _is) {
            this.is = _is;
        }

        @Override
        @SuppressWarnings("CatchAndPrintStackTrace")
        public void run() {
            System.out.println("Reading responses from server ...");
            int x;
            try {
                while ((x = is.read()) > -1) {
                    char c = (char) x;
                    System.out.print(c);
                }
            } catch (IOException e) {
                System.err.println("Error while reading server response: " + e.getMessage());
                e.printStackTrace();
            } finally {
                System.out.println("... disconnected from server.");
            }
        }
    }
}



