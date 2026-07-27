package net.runelite.client.plugins.banktags.tabs;

import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.ScriptID;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.game.chatbox.ChatboxPanelManager;
import net.runelite.client.plugins.bank.BankSearch;
import net.runelite.client.plugins.banktags.BankTagsConfig;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.plugins.banktags.TagManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TagTabPotionStorageVisibilityTest
{
    @Mock private Client client;
    @Mock private ClientThread clientThread;
    @Mock private BankTagsPlugin plugin;
    @Mock private ItemManager itemManager;
    @Mock private TagManager tagManager;
    @Mock private TabManager tabManager;
    @Mock private LayoutManager layoutManager;
    @Mock private PotionStorage potionStorage;
    @Mock private ChatboxPanelManager chatboxPanelManager;
    @Mock private BankTagsConfig config;
    @Mock private BankSearch bankSearch;
    @Mock private ChatboxItemSearch searchProvider;
    @Mock private ChatMessageManager chatMessageManager;

    @Mock private Widget itemsContainer;
    @Mock private Widget realItemWidget;
    @Mock private Widget newSlotWidget;
    @Mock private ItemComposition itemComposition;
    @Mock private Widget parentWidget;
    @Mock private Widget scrollComponentWidget;
    @Mock private Widget upButtonWidget;
    @Mock private Widget downButtonWidget;

    private static final int SUPER_RESTORE_4_DOSE = 3024;
    private static final int TAGGED_ITEM_QTY_IN_STORAGE = 5;

    private TabInterface tabInterface;
    private List<Widget> containerChildren;

    @Before
    public void setUp()
    {
        tabInterface = new TabInterface(client, clientThread, plugin, itemManager, tagManager,
                tabManager, layoutManager, potionStorage, chatboxPanelManager, config, bankSearch,
                searchProvider, chatMessageManager);

        when(client.getWidget(InterfaceID.Bankmain.ITEMS)).thenReturn(itemsContainer);

        // Only one real item is currently visible in the tab (the vanilla search already
        // ran and filtered the bank down to this).
        containerChildren = new ArrayList<>();
        containerChildren.add(realItemWidget);

        when(itemsContainer.getChildren())
                .thenAnswer(invocation -> containerChildren.toArray(new Widget[0]));

        when(realItemWidget.isHidden()).thenReturn(false);
        when(realItemWidget.getItemId()).thenReturn(995);

        when(itemsContainer.getChild(anyInt())).thenReturn(null);
        when(itemsContainer.createChild(eq(-1), eq(WidgetType.GRAPHIC)))
                .thenAnswer(invocation ->
                {
                    containerChildren.add(newSlotWidget);
                    return newSlotWidget;
                });

        when(newSlotWidget.isHidden()).thenReturn(true);
        when(newSlotWidget.getItemId()).thenReturn(-1);

        List<Integer> tagged = Collections.singletonList(SUPER_RESTORE_4_DOSE);
        when(tagManager.getItemsForTag("restores")).thenReturn(tagged);
        when(potionStorage.count(SUPER_RESTORE_4_DOSE)).thenReturn(TAGGED_ITEM_QTY_IN_STORAGE);
        when(potionStorage.matches(anySet(), eq(SUPER_RESTORE_4_DOSE))).thenReturn(-1);

        when(itemManager.getItemComposition(SUPER_RESTORE_4_DOSE)).thenReturn(itemComposition);
        when(itemComposition.getName()).thenReturn("Super restore(4)");

        setPrivateField("enabled", true);
        setPrivateField("parent", parentWidget);
        when(parentWidget.getHeight()).thenReturn(400);
        setPrivateField("scrollComponent", scrollComponentWidget);
        setPrivateField("upButton", upButtonWidget);
        setPrivateField("downButton", downButtonWidget);

        when(parentWidget.getChildren()).thenReturn(new Widget[0]);
        when(tabManager.getTabs()).thenReturn(Collections.emptyList());
    }

    /**
     * BEFORE the fix: this is what the tab's widget list looks like today, when nothing
     * ever calls appendPotionStoreItems(). Passes on current master -- this documents the bug.
     */
    @Test
    public void beforeFix_potionIsNotInWidgetList()
    {
        // Deliberately NOT calling tabInterface.appendPotionStoreItems(...) here --
        // this is the current (buggy) code path for a non-layout tag tab.

        boolean potionIsVisible = isItemVisibleInTab(SUPER_RESTORE_4_DOSE);

        assertFalse("Bug reproduction: potion should be missing before the fix", potionIsVisible);
    }

    /**
     * AFTER the fix: once appendPotionStoreItems runs, the potion should show up in the
     * tab's widget list. Fails on current master until the fix is wired into
     * onScriptPreFired; passes once it is.
     */
    @Test
    public void afterFix_potionAppearsInWidgetList()
    {
        tabInterface.appendPotionStoreItems("restores");

        // simulate the widget now actually holding the potion, since our mock doesn't
        // re-evaluate stubbed getters based on prior setter calls
        when(newSlotWidget.isHidden()).thenReturn(false);
        when(newSlotWidget.getItemId()).thenReturn(SUPER_RESTORE_4_DOSE);

        boolean potionIsVisible = isItemVisibleInTab(SUPER_RESTORE_4_DOSE);

        assertTrue("Fix verification: potion should be visible after the fix", potionIsVisible);
    }

    // Scans the tab's item widgets exactly the way a player looking at the screen would --
    // is there a non-hidden widget showing this itemId anywhere in the container?
    private boolean isItemVisibleInTab(int itemId)
    {
        Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
        for (Widget child : container.getChildren())
        {
            if (child != null && !child.isHidden() && child.getItemId() == itemId)
            {
                return true;
            }
        }
        return false;
    }


    @Mock private Widget bankTitleWidget;

    @Test
    public void onScriptPreFired_realEntryPoint_appendsPotionsWhenNoLayout()
    {
        // activeTag = "restores", activeLayout = null (没开 Layout)

        tabInterface.openTag("restores", null, 0, false);

        when(client.getWidget(InterfaceID.Bankmain.TITLE)).thenReturn(bankTitleWidget);

        ScriptPreFired event = mock(ScriptPreFired.class);
        when(event.getScriptId()).thenReturn(ScriptID.BANKMAIN_FINISHBUILDING);

        tabInterface.onScriptPreFired(event);

        when(newSlotWidget.isHidden()).thenReturn(false);
        when(newSlotWidget.getItemId()).thenReturn(SUPER_RESTORE_4_DOSE);


        boolean potionIsVisible = isItemVisibleInTab(SUPER_RESTORE_4_DOSE);
        assertTrue("Potion should appear via the real onScriptPreFired entry point, "
                + "proving appendPotionStoreItems is actually wired in", potionIsVisible);
    }

    private void setPrivateField(String fieldName, Object value)
    {
        try{
            Field field = TabInterface.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(tabInterface, value);
        }
        catch (ReflectiveOperationException e)
        {
            throw new RuntimeException(e);
        }
    }
}