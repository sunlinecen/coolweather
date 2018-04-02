package com.coolweather.android;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.android.db.City;
import com.coolweather.android.db.County;
import com.coolweather.android.db.Province;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;


public class ChooseAreaFragment extends Fragment {

    private static final String TAG = "ChooseAreaFragment";

    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;

    private TextView titleText;

    private Button backButton;

    private ListView listView;

    private ArrayAdapter<String> adapter;

    private ArrayList<String> datalist = new ArrayList<>();

    private List<Province> provinceList;

    private List<City> cityList;

    private List<County> countyList;

    private Province selectProvince;

    private City selectCity;
    /**
     * 当前被选中的级别
     */
    private int curruntLevel;


    public ChooseAreaFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        backButton = view.findViewById(R.id.back_button);
        titleText = view.findViewById(R.id.title_text);
        listView = view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, datalist);
        listView.setAdapter(adapter);

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (curruntLevel == LEVEL_PROVINCE) {
                    selectProvince = provinceList.get(position);
                    queryCities();
                } else if (curruntLevel == LEVEL_CITY) {
                    selectCity = cityList.get(position);
                    queryCounty();
                } else if (curruntLevel == LEVEL_COUNTY) {
                    String weatherId = countyList.get(position).getWeatherId();
                    if (getActivity() instanceof MainActivity) {
                        Intent intent = new Intent(getActivity(), WeatherActivity.class);
                        intent.putExtra("weather_id", weatherId);
                        startActivity(intent);
                        getActivity().finish();
                    }else if(getActivity()instanceof WeatherActivity){
                        WeatherActivity activity=(WeatherActivity)getActivity();
                        activity.drawerLayout.closeDrawers();
                        activity.swipeRefresh.setRefreshing(true);
                        activity.requestWeather(weatherId);
                    }
                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (curruntLevel == LEVEL_COUNTY) {
                    queryCities();
                } else if (curruntLevel == LEVEL_CITY) {
                    queryProvince();
                }
            }
        });
        queryProvince();
    }

    /**
     * 查询省级数据，优先从数据库中查询
     */
    private void queryProvince() {
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        provinceList = DataSupport.findAll(Province.class);
        if (provinceList.size() > 0) {
            datalist.clear();
            for (Province province : provinceList) {
                datalist.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            curruntLevel = LEVEL_PROVINCE;
        } else {
            String address = "http://guolin.tech/api/china";
            Log.d(TAG, "queryProvince: " + address);
            queryFromServer(address, "province");
        }
    }


    /**
     * 查询县,优先从数据库中查询
     */

    private void queryCounty() {
        titleText.setText(selectCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = DataSupport.where("cityId = ?", String.valueOf(selectCity.getId())).find(County.class);
        if (countyList.size() > 0) {
            datalist.clear();
            for (County county : countyList) {
                datalist.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            curruntLevel = LEVEL_COUNTY;
        } else {
            int provinceId = selectProvince.getId();
            int cityId = selectCity.getCityCode();
            Log.d(TAG, "queryCounty: " + selectCity.getCityName());
            Log.d(TAG, "queryCounty: " + selectCity.getCityCode());
            String address = "http://guolin.tech/api/china/" + provinceId + "/" + cityId;
            Log.d(TAG, "queryCounty: " + address);
            queryFromServer(address, "county");
        }
    }

    /**
     * 查询城市
     */
    private void queryCities() {

        titleText.setText(selectProvince.getProvinceName());
        backButton.setVisibility(View.VISIBLE);
//        int provinceId=selectProvince.getId();
        cityList = DataSupport.where("provinceId = ?", String.valueOf(selectProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            datalist.clear();
            for (City city : cityList) {
                datalist.add(city.getCityName());
                Log.d(TAG, "queryCities: commit the branch if true" + city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            curruntLevel = LEVEL_CITY;
            Log.d(TAG, "queryCities: commit the branch if true");
        } else {
            int provinceId = selectProvince.getProvinceCode();
            String address = "http://guolin.tech/api/china/" + provinceId;
            Log.d(TAG, "queryCities: " + address);
            queryFromServer(address, "city");
        }
//        Log.d(TAG, "queryCities: ");
    }

    /**
     * 从服务器中查询数据
     *
     * @param address
     * @param type
     */
    private void queryFromServer(String address, final String type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(), "加载失败", Toast.LENGTH_SHORT).show();
                        Log.d(TAG, "queryFromServer:onFailure ");
                    }
                });

            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                if (type.equals("province")) {
                    result = Utility.handleProvinceResponse(responseText);
                } else if (type.equals("city")) {
                    result = Utility.handleCityResponse(responseText, selectProvince.getId());
                } else if (type.equals("county")) {
                    result = Utility.handleCountyResponse(responseText, selectCity.getId());
                    Log.d(TAG, "onResponse: the result" + result);
                    Log.d(TAG, "onResponse: the city id is " + selectCity.getId());
                }
                /**
                 * 处理了服务器数据后，判断是否处理成功，是则调用查询功能继续显示，否则Toast提示
                 */
                if (result) {
                    Log.d(TAG, "queryFromServer:onResponse ");
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if (type.equals("province")) {
                                queryProvince();
                            } else if (type.equals("city")) {
                                queryCities();
                            } else if (type.equals("county")) {
                                queryCounty();
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 关闭进度框显示
     */
    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
            Log.d(TAG, "closeProgressDialog: ");
        }

    }

    /**
     * 显示进度
     */
    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
        Log.d(TAG, "showProgressDialog: ");
    }
}
