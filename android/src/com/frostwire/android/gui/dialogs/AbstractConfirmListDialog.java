/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011, 2014, FrostWire(TM). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.android.gui.dialogs;

import android.app.Dialog;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

import com.frostwire.android.R;
import com.frostwire.android.gui.views.AbstractDialog;
import com.frostwire.logging.Logger;
import com.frostwire.search.SearchResult;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * This dialog should evolve to allow us for reuse on a number of situations in which you
 * need a dialog that needs to display a list view control.
 * 
 * This would be the simplest version, in the future it will have a text editor to filter
 * the contents of the list, and it will also support different modes of selection.
 * 
 * For now it just uses an adapter to display the contents of the model data.
 * 
 * It's up to the user to implement the adapter (hmm, perhaps that's where the selection mode logic should be)
 * 
 * @author aldenml
 * @author gubatron
 * @author votaguz
 */
public abstract class AbstractConfirmListDialog<T extends SearchResult> extends AbstractDialog {

    /**
     * TODOS: 1. Add an optional text filter control that will be connected to the adapter.
     */

    Logger LOGGER = Logger.getLogger(AbstractConfirmListDialog.class);

    public enum SelectionMode {
        NO_SELECTION,
        SINGLE_SELECTION,
        MULTIPLE_SELECTION,
    }

    private final static String TAG = "confirm_list_dialog";
    private String title;
    private String dialogText;
    private final SelectionMode selectionMode;
    private OnCancelListener onCancelListener;
    private OnClickListener onYesListener;
    private ConfirmListDialogDefaultAdapter<T> customAdapter;
    private ConfirmListDialogDefaultAdapter<T> adapter;

    abstract protected OnClickListener createOnYesListener(AbstractConfirmListDialog dlg);

    /** rebuilds list of objects from json and does listView.setAdapter(YourAdapter(theObjectList)) */
    abstract public List<T> deserializeData(String listDataInJSON);

    public AbstractConfirmListDialog(SelectionMode selectionMode,
                                     ConfirmListDialogDefaultAdapter customAdapter) {
        super(TAG, R.layout.dialog_confirm_list);
        this.selectionMode = selectionMode;
        this.customAdapter = customAdapter;
        this.adapter = customAdapter;
    }

    public AbstractConfirmListDialog(SelectionMode selectionMode) {
        this(selectionMode, null);
    }

    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    protected void prepareArguments(String dialogTitle,
                                    String dialogText,
                                    String listDataInJSON,
                                    SelectionMode selectionMode) {
        Bundle bundle = new Bundle();
        bundle.putString("title", dialogTitle);
        bundle.putString("dialogText", dialogText);
        bundle.putString("listData", listDataInJSON);
        bundle.putInt("selectionMode", selectionMode.ordinal());
        setArguments(bundle);
    }

    protected void prepareArguments(String dialogTitle, String dialogText, String listDataInJSON){
        prepareArguments(dialogTitle, dialogText, listDataInJSON, selectionMode);
    }

    @Override
    protected void initComponents(Dialog dlg, Bundle savedInstanceState) {
    	//TODO: Make sure the adapter
    	//will know what items have been selected and that we can
    	//reinit the right checkboxes selected here when orientation change occurs.
    	
        Bundle bundle = getArguments();
        title = bundle.getString("title");
        dlg.setTitle(title);
        
        dialogText = bundle.getString("dialogText");
        TextView textView = findView(dlg, R.id.dialog_confirm_list_text);
        textView.setText(dialogText);


        if (selectionMode == SelectionMode.MULTIPLE_SELECTION) {
            CheckBox checkBox = findView(dlg, R.id.dialog_confirm_list_select_all_checkbox);
            checkBox.setVisibility(View.VISIBLE);
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    if (buttonView.isChecked()) {
                        adapter.checkAll();
                    } else {
                        adapter.clearChecked();
                    }
                }
            });
        }

        ListView listView = findView(dlg, R.id.dialog_confirm_list_list);
        String listDataString = bundle.getString("listData");
        List<T> listData = deserializeData(listDataString);

        if (customAdapter != null) {
            customAdapter.addList(listData);
            adapter = customAdapter;
        } else {
            adapter = new ConfirmListDialogDefaultAdapter<>(getActivity(),
                            listData,
                            selectionMode);
        }

        listView.setAdapter(adapter);
        
        final Dialog dialog = dlg;
        Button noButton = findView(dialog, R.id.dialog_confirm_list_button_no);
        noButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onCancelListener != null) {
                    onCancelListener.onCancel(dialog);
                }
                dialog.dismiss();
            }
        });
        
        if (onCancelListener != null){
            dialog.setOnCancelListener(onCancelListener);
        }
        
        onYesListener = createOnYesListener(this);

        if (onYesListener != null) {
        	Button yesButton = findView(dialog, R.id.dialog_confirm_list_button_yes);
        	yesButton.setOnClickListener(onYesListener);
        }
    }
    
    public void setOnYesListener(OnClickListener listener) {
        onYesListener = listener;
    }

    public OnClickListener getOnYesListener() {
        return onYesListener;
    }

    public Set<T> getChecked() {
        Set<T> result = Collections.EMPTY_SET;
        if (adapter != null) {
            result = adapter.getChecked();
        }
        return result;
    }

    public List<T> getList() {
        List<T> result = Collections.EMPTY_LIST;
        if (adapter != null) {
            result = adapter.getList();
        }
        return result;
    }

    public boolean[] getSelected() {
        boolean[] result = new boolean[0];
        if (adapter != null) {
            List<T> checked = (List<T>) adapter.getChecked();

            if (checked == null || checked.isEmpty()) {
                return result;
            }

            result = new boolean[checked.size()];

            List<T> all = adapter.getList();

            Iterator<T> iterator = checked.iterator();

            while (iterator.hasNext()) {
                T item = iterator.next();
                int i = all.indexOf(item);
                if (i >= 0 && i < result.length) {
                    result[i]=true;
                } else {
                    LOGGER.warn("getSelected() is not finding the checked items on the list. Verify your classes implement equals() and hashCode()");
                }
            }
        }
        return result;
    }
}