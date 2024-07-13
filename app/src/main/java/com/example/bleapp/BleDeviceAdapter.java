package com.example.bleapp;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.clj.fastble.data.BleDevice;

import java.util.ArrayList;
import java.util.List;

public class BleDeviceAdapter extends RecyclerView.Adapter<BleDeviceAdapter.BleDeviceViewHolder> {

    private List<BleDevice> bleDeviceList = new ArrayList<>();
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(BleDevice device);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void addDevice(BleDevice device) {
        if (!bleDeviceList.contains(device)) {
            bleDeviceList.add(device);
            notifyItemInserted(bleDeviceList.size() - 1);
        }
    }

    @NonNull
    @Override
    public BleDeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new BleDeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BleDeviceViewHolder holder, int position) {
        BleDevice device = bleDeviceList.get(position);
        holder.deviceName.setText(device.getName() != null ? device.getName() : "Unknown Device");

        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(device);
            }
        });
    }

    @Override
    public int getItemCount() {
        return bleDeviceList.size();
    }

    static class BleDeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;

        BleDeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(android.R.id.text1);
        }
    }
}
