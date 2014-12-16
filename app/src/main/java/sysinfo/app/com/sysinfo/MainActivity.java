package sysinfo.app.com.sysinfo;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

import sysinfo.app.com.sysinfo.mms.WorkingMessage;
import sysinfo.app.com.sysinfo.util.LogTag;
import sysinfo.app.com.sysinfo.util.MmsUtil;


public class MainActivity extends ActionBarActivity implements View.OnClickListener, WorkingMessage.MessageStatusListener {

    private static final String TAG = "MainActivity";
    EditText numberText;
    Button sendBtn;
    EditText bodyText;
    ListView mSendListView;
    SendListAdpater mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        numberText = (EditText) findViewById(R.id.number);
        sendBtn = (Button) findViewById(R.id.send);
        bodyText = (EditText) findViewById(R.id.body);

        mSendListView = (ListView) findViewById(R.id.sendList);

        mAdapter = new SendListAdpater(this, 0);
        ArrayList<Message> mList = new ArrayList<>();
        Message m = null;
        for (int i = 0; i < 10 ;i++) {
            m = new Message();
            m.body = "body:"+i;
            m.number = ""+i;
            m.msgId = i;
//            mList.add(m);
        }
        mAdapter.addAll(mList);
        mSendListView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();
        sendBtn.setOnClickListener(this);

        if (!MmsUtil.isSmsEnabled(this)) {
            LogTag.i(TAG, "not sms enabel");
            MmsUtil.setDefaultMms(this, 100);
        }

        startService(new Intent(this, MonitorService.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        LogTag.i(TAG, "requestCode:" + requestCode + ",resultCode:" + resultCode);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        final String body = bodyText.getText().toString();
        final String number = numberText.getText().toString();
        if (TextUtils.isEmpty(number)) {
            Toast.makeText(this, "短信号码不能为空!", Toast.LENGTH_SHORT).show();
            return;
        } else if (TextUtils.isEmpty(body)) {
            Toast.makeText(this, "短信内容不能为空!", Toast.LENGTH_SHORT).show();
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    WorkingMessage.create(MainActivity.this).preSendSmsWorker(body, number, -1);
                } catch (Exception e) {
                    MyCrashHandler.dumpCrashToSD(e);
                }
            }
        });
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
//        　　imm.showSoftInput(editText, 0);
        imm.hideSoftInputFromWindow(numberText.getWindowToken(), 0);

    }

    @Override
    public void onProtocolChanged(boolean mms) {

    }

    @Override
    public void onAttachmentChanged() {

    }

    @Override
    public void onPreMessageSent() {

    }

    @Override
    public void onMessageSent() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "发送完成", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onMaxPendingMessagesReached() {

    }

    @Override
    public void onAttachmentError(int error) {

    }
}
