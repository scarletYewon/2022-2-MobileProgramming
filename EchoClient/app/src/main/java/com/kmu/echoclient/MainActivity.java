package com.kmu.echoclient;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private ClientThread mClientThread;
    private EditText mEditIP, mEditData;
    private Button mBtnConnect, mBtnSend;
    private TextView mTextOutput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEditIP = (EditText) findViewById(R.id.editIP);
        mEditData = (EditText) findViewById(R.id.editData);
        mBtnConnect = (Button) findViewById(R.id.btnConnect);
        mBtnSend = (Button) findViewById(R.id.btnSend);
        mTextOutput = (TextView) findViewById(R.id.textOutput);
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void mOnClick(View v) {
        switch (v.getId()) {
            case R.id.btnConnect:
                if (mClientThread == null) {
                    String str = mEditIP.getText().toString();
                    if (str.length() !=0) {
                        mClientThread = new ClientThread(str, mMainHandler);
                        mClientThread.start();
                        mBtnConnect.setEnabled(false);
                        mBtnSend.setEnabled(true);
                    }
                }
                break;
            case R.id.btnQuit:
                finish();
                break;
            case R.id.btnSend:
                if (SendThread.mHandler != null) {
                    Message msg = Message.obtain();
                    msg.what = 1;
                    msg.obj = mEditData.getText().toString();
                    SendThread.mHandler.sendMessage(msg);
                    mEditData.selectAll();
                }
                break;
        }
    }

    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    mTextOutput.append((String) msg.obj);
                    break;
            }
        }
    };
}

class ClientThread extends Thread {

    private String mServAddr;
    private Handler mMainHandler;

    public ClientThread(String servAddr, Handler mainHandler) {
        mServAddr = servAddr;
        mMainHandler = mainHandler;
    }

    @Override
    public void run() {
        Socket sock = null;
        try {
            sock = new Socket(mServAddr, 9000);
            doPrintln(">> 서버와 연결 성공!");
            SendThread sendThread = new SendThread(this, sock.getOutputStream());
            RecvThread recvThread = new RecvThread(this, sock.getInputStream());
            sendThread.start();
            recvThread.start();
            sendThread.join();
            recvThread.join();
        } catch (Exception e) {
            doPrintln(e.getMessage());
        } finally {
            try {
                if (sock != null) {
                    sock.close();
                    doPrintln(">> 서버와 연결 종료!");
                }
            } catch (IOException e) {
                doPrintln(e.getMessage());
            }
        }
    }

    public void doPrintln(String str) {
        Message msg = Message.obtain();
        msg.what = 1;
        msg.obj = str + "\n";
        mMainHandler.sendMessage(msg);
    }
}

class SendThread extends Thread {

    private ClientThread mClientThread;
    private OutputStream mOutStream;
    public static Handler mHandler;

    public SendThread(ClientThread clientThread, OutputStream outStream) {
        mClientThread = clientThread;
        mOutStream = outStream;
    }

    @Override
    public void run() {
        Looper.prepare();
        mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 1:
                        try {
                            String s = (String) msg.obj;
                            mOutStream.write(s.getBytes());
                            mClientThread.doPrintln("[보낸 데이터]" + s);
                        } catch (IOException e) {
                            mClientThread.doPrintln(e.getMessage());
                        }
                        break;
                    case 2:
                        getLooper().quit();
                        break;
                }
            }
        };
        Looper.loop();
    }
}

class RecvThread extends Thread {
    private ClientThread mClientThread;
    private InputStream mInStream;

    public RecvThread(ClientThread clientThread, InputStream inStream) {
        mClientThread = clientThread;
        mInStream = inStream;
    }

    @Override
    public void run() {
        byte[] buf = new byte[1024];
        while (true) {
            try {
                int nbytes = mInStream.read(buf);
                if (nbytes > 0) {
                    String s = new String(buf,0,nbytes);
                    mClientThread.doPrintln("[받은 데이터]" + s);
                } else {
                    mClientThread.doPrintln(">> 서버가 연결 끊음!");
                    if (SendThread.mHandler != null) {
                        Message msg = Message.obtain();
                        msg.what = 2;
                        SendThread.mHandler.sendMessage(msg);
                    }
                    break;
                }
            } catch (IOException e) {
                mClientThread.doPrintln(e.getMessage());
            }
        }
    }
}