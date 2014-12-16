package sysinfo.app.com.sysinfo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.zip.Inflater;

/**
 * Created by dufan on 2014/12/8.
 */
public class SendListAdpater extends ArrayAdapter<Message> {

    private LayoutInflater mInflater;

    public SendListAdpater(Context context, int resource) {
        super(context, resource);
        mInflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Message msg = getItem(position);
        convertView = mInflater.inflate(R.layout.send_item, null, false);
        TextView number = (TextView) convertView.findViewById(R.id.number);
        TextView body = (TextView) convertView.findViewById(R.id.body);
        number.setText(msg.number);
        body.setText(msg.body);
        return convertView;
    }


}
