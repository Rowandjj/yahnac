package com.malmstein.yahnac.stories;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.malmstein.yahnac.HNewsActivity;
import com.malmstein.yahnac.R;
import com.malmstein.yahnac.data.DataPersister;
import com.malmstein.yahnac.inject.Inject;
import com.malmstein.yahnac.model.Story;
import com.malmstein.yahnac.presenters.StoriesPagerAdapter;
import com.malmstein.yahnac.views.SnackBarView;
import com.malmstein.yahnac.views.sliding_tabs.SlidingTabLayout;
import com.novoda.notils.caster.Views;

public class NewsActivity extends HNewsActivity implements StoryListener{

    public static final int INITIAL_PAGE = 1;
    private static final int OFFSCREEN_PAGE_LIMIT = 1;
    private ViewPager headersPager;
    private SlidingTabLayout slidingTabs;
    private StoriesPagerAdapter headersAdapter;

    private SnackBarView snackbarView;
    private int croutonAnimationDuration;
    private int croutonBackgroundAlpha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_news);

        setupHeaders();
        setupTabs();
        setupSnackbar();
        setupAppBar();
    }

    private void setupSnackbar() {
        snackbarView = Views.findById(this, R.id.snackbar);
        croutonBackgroundAlpha = getResources().getInteger(R.integer.feed_crouton_background_alpha);
        croutonAnimationDuration = getResources().getInteger(R.integer.feed_crouton_animation_duration);
    }

    private void setupHeaders() {
        headersAdapter = new StoriesPagerAdapter(getSupportFragmentManager());
        headersPager = Views.findById(this, R.id.news_pager);
        headersPager.setOffscreenPageLimit(OFFSCREEN_PAGE_LIMIT);
        headersPager.setAdapter(headersAdapter);
        headersPager.setCurrentItem(INITIAL_PAGE);
    }

    private void setupTabs() {
        slidingTabs = Views.findById(this, R.id.sliding_tabs);
        slidingTabs.setCustomTabView(R.layout.view_tab_indicator, android.R.id.text1);
        slidingTabs.setViewPager(headersPager);
        slidingTabs.setSelectedIndicatorColors(getResources().getColor(R.color.feed_tabs_selected_indicator));
        slidingTabs.setOnPageChangeListener(new StoryOnPageChangeListener());
    }

    private void setupAppBar() {
        setTitle(getString(R.string.title_app));

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_about) {
            navigate().toSettings();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onShareClicked(Intent shareIntent) {
        startActivity(shareIntent);
    }

    @Override
    public void onCommentsClicked(View v, Story story) {
        navigate().toComments(story);
    }

    @Override
    public void onContentClicked(Story story) {
        DataPersister persister = Inject.dataPersister();
        persister.markStoryAsRead(story);
        if (story.isHackerNewsLocalItem()) {
            navigate().toComments(story);
        } else {
            navigate().toInnerBrowser(story);
        }
    }

    @Override
    public void onExternalLinkClicked(Story story) {
        if (story.isHackerNewsLocalItem()) {
            navigate().toComments(story);
        } else {
            navigate().toExternalBrowser(Uri.parse(story.getUrl()));
        }
    }

    @Override
    public void onBookmarkClicked(Story story) {
        DataPersister persister = Inject.dataPersister();
        if (story.isBookmark()) {
            removeBookmark(persister, story);
        } else {
            addBookmark(persister, story);
        }
    }

    @Override
    public void onQuickReturnVisibilityChangeHint(boolean visible) {
        if (visible) {
            getAppBarContainer().showAppBar();
        } else {
            getAppBarContainer().hideAppBar();
        }
    }

    private void removeBookmark(DataPersister persister, Story story) {
        persister.removeBookmark(story);
        showRemovedBookmarkSnackbar(persister, story);
    }

    private void addBookmark(DataPersister persister, Story story) {
        persister.addBookmark(story);
        showAddedBookmarkSnackbar(persister, story);
    }

    private void showAddedBookmarkSnackbar(final DataPersister persister, final Story story) {
        snackbarView.showSnackBar(getResources().getText(R.string.feed_snackbar_added_bookmark))
                .withBackgroundColor(R.color.black, croutonBackgroundAlpha)
                .withAnimationDuration(croutonAnimationDuration)
                .withUndoClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackbarView.hideCrouton();
                        removeBookmark(persister, story);
                    }
                })
                .animating();
    }

    private void showRemovedBookmarkSnackbar(final DataPersister persister, final Story story) {
        snackbarView.showSnackBar(getResources().getText(R.string.feed_snackbar_removed_bookmark))
                .withBackgroundColor(R.color.black, croutonBackgroundAlpha)
                .withAnimationDuration(croutonAnimationDuration)
                .withUndoClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackbarView.hideCrouton();
                        addBookmark(persister, story);
                    }
                })
                .animating();
    }

    private class StoryOnPageChangeListener extends ViewPager.SimpleOnPageChangeListener {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            if (positionOffset == 0) {
                return;
            }
            scrollFeedsAccordingToAppBarVisibility();
        }

        private void scrollFeedsAccordingToAppBarVisibility() {
            Fragment currentFragment = headersAdapter.getPrimaryItem();
            int px = getAppBarContainer().isAppBarShowing() ? 0 : getAppBarContainer().getHideableHeightPx();
            for (int i = 0; i < headersAdapter.getCount(); i++) {
                if (i == 0){
                    BookmarksFragment fragment = getBookmarkFragmentAt(i);
                    if (fragment == null || fragment == currentFragment) {
                        continue;
                    }
                    if (fragment.shouldBeScrolledToTop()) {
                        fragment.scrollToTopWithOffset(px);
                    }
                } else {
                    StoryFragment fragment = getStoryFragmentAt(i);
                    if (fragment == null || fragment == currentFragment) {
                        continue;
                    }
                    if (fragment.shouldBeScrolledToTop()) {
                        fragment.scrollToTopWithOffset(px);
                    }
                }

            }
        }

        private StoryFragment getStoryFragmentAt(int position) {
            String tag = headersAdapter.getTag(position);
            return (StoryFragment) getSupportFragmentManager().findFragmentByTag(tag);
        }

        private BookmarksFragment getBookmarkFragmentAt(int position) {
            String tag = headersAdapter.getTag(position);
            return (BookmarksFragment) getSupportFragmentManager().findFragmentByTag(tag);
        }


    }
}
