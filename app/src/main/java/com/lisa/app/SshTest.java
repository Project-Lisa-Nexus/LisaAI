package com.lisa.app;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;

public class SshTest {
    public static void test() throws Exception {
        JSch jsch = new JSch();
        KeyPair kp = KeyPair.genKeyPair(jsch, KeyPair.ED25519);
        kp.dispose();
    }
}
