package com.googlecode.networklog;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;

public class HostNames {
    JSONObject jsonData;
    ArrayList<HashMap<String, String>> hosts;
    NetworkResolver resolver;

    public HostNames(Context context) {
        resolver = new NetworkResolver();
        try {
            this.jsonData = new JSONObject(loadJSONFromAsset(context));
            JSONArray m_jArry = jsonData.getJSONArray("domains");
            hosts = new ArrayList<>();
            HashMap<String, String> m_li;

            for (int i = 0; i < m_jArry.length(); i++) {
                JSONObject jo_inside = m_jArry.getJSONObject(i);
                Log.d("Details-->", jo_inside.getString("address"));
                String addr = jo_inside.getString("address");
                String name = jo_inside.getString("name");

                m_li = new HashMap<>();
                m_li.put("address", addr);
                m_li.put("name", name);

                hosts.add(m_li);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getName(String address) {
        for (HashMap<String, String> m : hosts) {
            if (m.get("address").equals(address))
                return m.get("name");
        }
        String resolvedAddr = resolver.getResolvedAddress(address);
        return resolvedAddr != null ? resolvedAddr : "Not found!";
    }

    public String loadJSONFromAsset(Context context) {
        String json;
        try {
            InputStream is = context.getAssets().open("hosts.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }
}
