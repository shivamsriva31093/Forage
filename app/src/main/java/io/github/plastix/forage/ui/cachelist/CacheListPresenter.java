package io.github.plastix.forage.ui.cachelist;

import android.location.Location;
import android.net.ConnectivityManager;

import com.google.gson.JsonArray;

import javax.inject.Inject;

import io.github.plastix.forage.data.api.OkApiInteractor;
import io.github.plastix.forage.data.local.DatabaseInteractor;
import io.github.plastix.forage.data.location.LocationInteractor;
import io.github.plastix.forage.ui.LifecycleCallbacks;
import io.github.plastix.forage.util.NetworkUtils;
import io.realm.Realm;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscription;
import rx.functions.Func1;
import rx.subscriptions.Subscriptions;

public class CacheListPresenter implements LifecycleCallbacks {

    private CacheListView view;
    private OkApiInteractor apiInteractor;
    private DatabaseInteractor databaseInteractor;
    private LocationInteractor locationInteractor;
    private ConnectivityManager connectivityManager;
    private Subscription subscription;


    @Inject
    public CacheListPresenter(CacheListView view, OkApiInteractor apiInteractor,
                              DatabaseInteractor databaseInteractor,
                              LocationInteractor locationInteractor,
                              ConnectivityManager connectivityManager) {
        this.view = view;
        this.apiInteractor = apiInteractor;
        this.databaseInteractor = databaseInteractor;
        this.locationInteractor = locationInteractor;
        this.connectivityManager = connectivityManager;
        this.subscription = Subscriptions.empty();
    }

    public void getCaches() {
        if (!NetworkUtils.hasInternetConnection(connectivityManager)) {
            view.onError();
        } else {

            this.subscription = locationInteractor.getUpdatedLocation().flatMap(new Func1<Location, Single<JsonArray>>() {
                @Override
                public Single<JsonArray> call(Location location) {
                    return apiInteractor.getNearbyCaches(location);
                }
            }).subscribe(new SingleSubscriber<JsonArray>() {
                @Override
                public void onSuccess(JsonArray value) {
                    databaseInteractor.saveCachesFromJson(value, new Realm.Transaction.Callback(){
                        @Override
                        public void onSuccess() {
                            view.updateList();
                        }
                    });
                }

                @Override
                public void onError(Throwable error) {
                    view.onError();
                }
            });

        }
    }


    @Override
    public void onStart() {
        locationInteractor.onStart();
    }

    @Override
    public void onStop() {
        locationInteractor.onStop();

    }

    @Override
    public void onResume() {

    }

    public void cancelRequest() {
        if (!subscription.isUnsubscribed()) {
            subscription.unsubscribe();
        }
    }


}