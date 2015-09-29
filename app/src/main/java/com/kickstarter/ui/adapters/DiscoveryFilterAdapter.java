package com.kickstarter.ui.adapters;

import android.util.Pair;
import android.view.View;

import com.kickstarter.R;
import com.kickstarter.models.Category;
import com.kickstarter.services.DiscoveryParams;
import com.kickstarter.ui.DiscoveryFilterStyle;
import com.kickstarter.ui.viewholders.DiscoveryFilterViewHolder;
import com.kickstarter.ui.viewholders.EmptyViewHolder;
import com.kickstarter.ui.viewholders.KsrViewHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import rx.Observable;

public class DiscoveryFilterAdapter extends KsrAdapter {
  private final Delegate delegate;
  private DiscoveryParams selectedDiscoveryParams;

  public interface Delegate extends DiscoveryFilterViewHolder.Delegate {}

  public DiscoveryFilterAdapter(final Delegate delegate, final DiscoveryParams selectedDiscoveryParams) {
    this.delegate = delegate;
    this.selectedDiscoveryParams = selectedDiscoveryParams;
  }

  protected int layout(final SectionRow sectionRow) {
    if (sectionRow.section() == 1) {
      return R.layout.discovery_filter_divider_view;
    }
    return R.layout.discovery_filter_view;
  }

  protected KsrViewHolder viewHolder(final int layout, final View view) {
    if (layout == R.layout.discovery_filter_divider_view) {
      return new EmptyViewHolder(view); // TODO: Might need to make a view holder here that toggles white or dark text
    }
    return new DiscoveryFilterViewHolder(view, delegate);
  }

  public void takeCategories(final List<Category> initialCategories) {
    data().clear();

    data().addAll(paramsSections(initialCategories).toList().toBlocking().single());
    data().add(1, Collections.singletonList(null)); // Category divider

    notifyDataSetChanged();
  }

  /**
   * Returns an Observable where each item is a list of params/style pairs.
   */
  protected Observable<List<Pair<DiscoveryParams, DiscoveryFilterStyle>>> paramsSections(final List<Category> initialCategories) {
    return categoryParams(initialCategories)
      .startWith(filterParams())
      .map(l -> Observable.from(l)
        .map(p -> Pair.create(p, DiscoveryFilterStyle.builder().primary(true).selected(true).visible(true).build())).toList().toBlocking().single());
  }

  /**
   * Params for the top section of filters.
   */
  protected Observable<List<DiscoveryParams>> filterParams() {
    // TODO: Add social filter
    return Observable.just(
      DiscoveryParams.builder().staffPicks(true).build(),
      DiscoveryParams.builder().starred(1).build(),
      DiscoveryParams.builder().build() // Everything filter
    ).toList();
  }

  /**
   * Transforms a list of categories into an Observable list of params.
   *
   * Each list of params has a duplicate root category. The duplicate will be used as a nested row under the
   * root downstream, e.g.:
   * Art
   *  - All of Art
   */
  protected Observable<List<DiscoveryParams>> categoryParams(final List<Category> initialCategories) {
    final Observable<Category> categories = Observable.from(initialCategories);

    final Observable<DiscoveryParams> params = categories
      .concatWith(categories.filter(Category::isRoot)) // Add the duplicate root category
      .map(c -> DiscoveryParams.builder().category(c).build())
      .toSortedList((p1, p2) -> p1.category().discoveryFilterCompareTo(p2.category()))
      .flatMap(Observable::from);

    // RxJava has groupBy. groupBy creates an Observable of GroupedObservables - the Observable doesn't complete
    // until all the GroupedObservables have been subscribed to and completed. It's quite confusing to work with,
    // refactor with caution.
    TreeMap<String, ArrayList<DiscoveryParams>> groupedParams = params.reduce(new TreeMap<String, ArrayList<DiscoveryParams>>(), (hash, p) -> {
      final String key = p.category().root().name();
      if (!hash.containsKey(key)) {
        hash.put(key, new ArrayList<DiscoveryParams>());
      }
      hash.get(key).add(p);
      return hash;
    }).toBlocking().single();

    return Observable.from(new ArrayList(groupedParams.values()));
  }
}
