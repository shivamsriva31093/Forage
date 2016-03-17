package io.github.plastix.forage.ui.cachelist;


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.StringRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import java.lang.ref.WeakReference;

import javax.inject.Inject;

import butterknife.Bind;
import io.github.plastix.forage.ForageApplication;
import io.github.plastix.forage.R;
import io.github.plastix.forage.data.local.model.Cache;
import io.github.plastix.forage.ui.PresenterFragment;
import io.github.plastix.forage.ui.SimpleDividerItemDecoration;
import io.github.plastix.forage.ui.cachedetail.CacheDetailActivity;
import io.github.plastix.forage.util.ActivityUtils;

/**
 * Fragment that is responsible for the Geocache list.
 */
public class CacheListFragment extends PresenterFragment<CacheListPresenter> implements CacheListView, SwipeRefreshLayout.OnRefreshListener, View.OnClickListener {

    @Inject
    CacheAdapter adapter;

    @Inject
    SimpleDividerItemDecoration itemDecorator;

    @Bind(R.id.cachelist_recyclerview)
    RecyclerView recyclerView;

    @Bind(R.id.cachelist_swiperefresh)
    SwipeRefreshLayout swipeRefreshLayout;

    @Bind(R.id.empty_view)
    View emptyView;

    private RecyclerView.AdapterDataObserver dataChangeListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tell the activity we have menu items to contribute to the toolbar
        setHasOptionsMenu(true);

        injectDependencies();
    }

    private void injectDependencies() {
        ForageApplication.getComponent(getContext())
                .plus(new CacheListModule(this)).injectTo(this);
    }

    @Override
    protected int getFragmentLayout() {
        return R.layout.fragment_cache_list;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ActivityUtils.setSupportActionBarTitle(getActivity(), R.string.cachelist_title);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(itemDecorator);
        recyclerView.setAdapter(adapter);

        this.dataChangeListener = new DataChangeListener(this);
        adapter.registerAdapterDataObserver(dataChangeListener);
        adapter.setOnClickListener(this);

        swipeRefreshLayout.setOnRefreshListener(this);
        updateEmptyView();
    }

    private void updateEmptyView() {
        stopRefresh();
        if (recyclerView.getAdapter() == null || adapter.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }

    }

    private void stopRefresh() {
        swipeRefreshLayout.setRefreshing(false);
    }

    @Override
    public void setRefreshing() {
        // Required hacky fix for some reason
        // http://stackoverflow.com/questions/26484907/setrefreshingtrue-does-not-show-indicator
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(true);
            }
        });
    }

    /**
     * Called when an item is clicked in the RecyclerView
     *
     * @param v View clicked.
     */
    @Override
    public void onClick(View v) {
        presenter.unsubscribe();
        stopRefresh();

        int position = recyclerView.getChildLayoutPosition(v);
        Cache cache = adapter.getItem(position);
        Intent intent = CacheDetailActivity.newIntent(getContext(), cache.cacheCode);

        // TODO Clean up
        Pair<View, String> one = Pair.create(v.findViewById(R.id.cache_name), "cache_title");
        Pair<View, String> two = Pair.create(v.findViewById(R.id.cache_type), "cache_type");

        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(), one, two);
        ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
    }

    @Override
    public void onErrorInternet() {
        makeErrorSnackbar(R.string.cachelist_error_no_internet);
    }

    private void makeErrorSnackbar(@StringRes int resID) {
        stopRefresh();
        Snackbar.make(recyclerView, resID, Snackbar.LENGTH_LONG)
                .setAction(R.string.cachelist_retry, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        downloadGeocaches();
                    }
                }).show();
    }

    private void downloadGeocaches() {
        swipeRefreshLayout.setRefreshing(true);
        presenter.fetchGeocaches();
    }

    @Override
    public void onErrorFetch() {
        makeErrorSnackbar(R.string.cachelist_error_failed_parse);
    }

    @Override
    public void onErrorLocation() {
        makeErrorSnackbar(R.string.cachelist_error_no_location);
    }

    /**
     * SwipeRefreshView callback.
     */
    @Override
    public void onRefresh() {
        downloadGeocaches();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu items from the Fragment's menu
        inflater.inflate(R.menu.menu_cache_list_fragment, menu);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_fetch:
                downloadGeocaches();
                return true;
            case R.id.action_clear:
                presenter.clearCaches();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Remove Butterknife bindings when the view is destroyed.
     */
    @Override
    public void onDestroyView() {
        recyclerView.setAdapter(null);
        adapter.unregisterAdapterDataObserver(dataChangeListener);
        adapter.setOnClickListener(null);
        this.dataChangeListener = null;

        super.onDestroyView();
    }

    /**
     * Clean up the resources when the fragment is destroyed.
     * e.g. Close the Realm instance held by the RecyclerView adapter.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        adapter.closeRealm();
    }

    private static class DataChangeListener extends RecyclerView.AdapterDataObserver {

        private final WeakReference<CacheListFragment> fragmentWeakReference;

        public DataChangeListener(CacheListFragment fragmentWeakReference) {
            this.fragmentWeakReference = new WeakReference<>(fragmentWeakReference);
        }

        @Override
        public void onChanged() {
            super.onChanged();
            CacheListFragment fragment = fragmentWeakReference.get();
            if (fragment != null) {
                fragment.updateEmptyView();
            }

        }
    }


}
