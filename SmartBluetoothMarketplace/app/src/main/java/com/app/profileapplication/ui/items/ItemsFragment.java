package com.app.profileapplication.ui.items;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.app.profileapplication.R;
import com.app.profileapplication.adapters.ItemsAdapter;
import com.app.profileapplication.models.Items;
import com.app.profileapplication.models.User;
import com.app.profileapplication.ui.cart.CartFragment;
import com.app.profileapplication.utilities.Parameters;
import com.estimote.coresdk.common.requirements.SystemRequirementsChecker;
import com.estimote.coresdk.observation.region.beacon.BeaconRegion;
import com.estimote.coresdk.recognition.packets.Beacon;
import com.estimote.coresdk.service.BeaconManager;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;


/**
 * A simple {@link Fragment} subclass.
 */
public class ItemsFragment extends Fragment implements ItemsAdapter.ItemsCartInterface {

    RecyclerView recyclerView;
    ArrayList<Items> itemsAdded = new ArrayList<>();
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private OkHttpClient client = new OkHttpClient();
    String message;
    String token;
    Button checkoutButton;
    private BeaconManager beaconManager;
    private BeaconRegion region;
    private static final String TAG = "ItemsFragment";

    private User user;
    private static DecimalFormat df2 = new DecimalFormat("#.##");
    ItemsAdapter itemsAdapter;
    private Double total = 0.0;
    String itemRegion;
    public ItemsFragment() {
        // Required empty public constructor
    }

    private static final Map<String, String> PLACES_BY_BEACONS;

    // TODO: replace "<major>:<minor>" strings to match your own beacons.
    static {
        Map<String, String> placesByBeacons = new HashMap<>();
        placesByBeacons.put("15326:56751", "lifestyle");
        placesByBeacons.put("41072:44931", "produce");
        placesByBeacons.put("47152:61548","grocery");
        //placesByBeacons.put("30462:43265","lifestyle");
        //placesByBeacons.put("26535:44799", "produce");
        //placesByBeacons.put("30476:29902","lifestyle");
        PLACES_BY_BEACONS = Collections.unmodifiableMap(placesByBeacons);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_items, container, false);
        beaconManager = new BeaconManager(getContext());
        region = new BeaconRegion("ranged region",
                null, null, null);

        beaconManager.setRangingListener((BeaconManager.BeaconRangingListener) (region, list) -> {
            if (!list.isEmpty()) {
                Beacon nearestBeacon = list.get(0);
                String tempRegion = placesNearBeacon(nearestBeacon);
                Log.d("Airport", "MeasuredPower: "+nearestBeacon.getMeasuredPower() + ", list size: " + list.size() + "--" + tempRegion);
                if (tempRegion!=null && !tempRegion.equalsIgnoreCase(itemRegion)){
                    itemRegion = tempRegion;
                    Log.d("Airport", "Nearest places: " + itemRegion + nearestBeacon.getMeasuredPower());
                    String responseString = getData(Parameters.API_URL+"/item/getItemsRegion?region="+itemRegion, view);
                }else if(tempRegion==null && itemRegion!=null){
                    itemRegion=null;
                    String responseString = getData(Parameters.API_URL+"/item/getItems", view);
                }
            }
        });

        token = getArguments().getString(Parameters.TOKEN);
        user = (User) getArguments().getSerializable(Parameters.USER_ID);
        recyclerView = view.findViewById(R.id.fragment_items_recyclerview);
        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 2));
        String responseString = getData(Parameters.API_URL+"/item/getItems", view);
        checkoutButton = view.findViewById(R.id.fragment_items_checkout);
        checkoutButton.setOnClickListener(view1 -> {
            Bundle bundle = new Bundle();
            bundle.putSerializable(Parameters.ITEM_LIST, itemsAdded);
            bundle.putString(Parameters.TOKEN, token);
            bundle.putSerializable(Parameters.USER_ID, user);
            CartFragment fragment = new CartFragment();
            fragment.setArguments(bundle);
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.nav_host_fragment, fragment).addToBackStack(null);
            fragmentTransaction.commit();
        });

        return view;
    }

    public String getData(String url, View view){
        OkHttpClient client = new OkHttpClient();
        ArrayList<Items> itemsArrayList = new ArrayList();
        final String[] responseString = new String[1];

        Request request = new Request.Builder()
                .url(url)
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) { }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                responseString[0] = response.body().string();

                try {
                    JSONObject jsonObject = new JSONObject(responseString[0]);
                    Iterator<String> keys = jsonObject.keys();

                    while(keys.hasNext()){

                        String key= keys.next();
                        JSONObject single_item = jsonObject.getJSONObject(key);
                        Double discountedPrice = single_item.getDouble(Parameters.ITEMS_ITEM_PRICE);

                        if (single_item.getDouble(Parameters.ITEMS_ITEM_DISCOUNT)>0)
                            discountedPrice = single_item.getDouble(Parameters.ITEMS_ITEM_PRICE) - ( single_item.getDouble(Parameters.ITEMS_ITEM_PRICE)* single_item.getDouble(Parameters.ITEMS_ITEM_DISCOUNT)/100);

                        Items items1 = new Items(
                                single_item.getString(Parameters.ITEMS_ITEM_NAME),
                                single_item.getString(Parameters.ITEMS_ITEM_REGION),
                                single_item.getString(Parameters.ITEMS_ITEM_ID),
                                Double.valueOf(df2.format(discountedPrice)),
                                single_item.getDouble(Parameters.ITEMS_ITEM_PRICE),
                                single_item.getString(Parameters.ITEMS_ITEM_PHOTO)
                        );
                        itemsArrayList.add(items1);

                    }
                    Comparator<Items> compareByRegion = (Items o1, Items o2) -> o1.getRegion().compareTo( o2.getRegion() );
                    Collections.sort(itemsArrayList, compareByRegion);
                    getActivity().runOnUiThread(() -> {
                        if (itemRegion!=null){
                            Toast.makeText(getContext(),itemRegion, Toast.LENGTH_LONG).show();
                        }else {
                            Toast.makeText(getContext(),"All Products", Toast.LENGTH_LONG).show();
                        }
                        itemsAdapter = new ItemsAdapter(getContext(), itemsArrayList, ItemsFragment.this::addToCart);
                        recyclerView.setAdapter(itemsAdapter);
                    });

                } catch (JSONException e) {
                    e.printStackTrace();
                }

            }
        });
        return responseString[0];

    }

    @Override
    public void addToCart(Items items) {
        itemsAdded.add(items);
        total+=items.getPrice();
        String url = Parameters.API_URL+"/user/addItem";
        String json = items.toString();
        Log.d("items11", json);
        try {
            JSONObject jsonObject = new JSONObject(json);
            jsonObject.put("discountPrice", 1);
            post(url, jsonObject.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void post(String url, String json) {
        RequestBody body = RequestBody.create(json, JSON);
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization",Parameters.BEARER + " " + token)
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override public void onResponse(Call call, Response response) throws IOException {
                try (ResponseBody responseBody = response.body()) {
                    if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

                    String responseString = responseBody.string();

                    try {
                        JSONObject json = new JSONObject(responseString);
                        message = (String) json.get(Parameters.MESSAGE);
                        Log.d("RESPONSE", message);
                        if (message.equalsIgnoreCase("Cart updated successfully!!")){
                            getActivity().runOnUiThread(() -> Toast.makeText(getContext(),message,Toast.LENGTH_LONG).show());
                        }
                    } catch (JSONException e){
                        e.printStackTrace();
                    }

                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        SystemRequirementsChecker.checkWithDefaultDialogs(getActivity());
        beaconManager.connect(new BeaconManager.ServiceReadyCallback() {
            @Override
            public void onServiceReady() {
                beaconManager.startRanging(region);
            }
        });
    }

    @Override
    public void onPause() {
        beaconManager.stopRanging(region);
        super.onPause();
    }

    private String placesNearBeacon(Beacon beacon) {
        String beaconKey = String.format("%d:%d", beacon.getMajor(), beacon.getMinor());
        String UUID = beacon.getProximityUUID().toString();
        Log.v(TAG,"Beacon Major: "+beaconKey+ ", UUID: "+UUID);
        if (PLACES_BY_BEACONS.containsKey(beaconKey)) {
            return PLACES_BY_BEACONS.get(beaconKey);
        }
        return null;
    }

}

