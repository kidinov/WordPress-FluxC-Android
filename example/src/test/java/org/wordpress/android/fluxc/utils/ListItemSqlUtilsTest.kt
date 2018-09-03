package org.wordpress.android.fluxc.utils

import com.yarolegovich.wellsql.WellSql
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.wordpress.android.fluxc.model.list.ListDescriptor
import org.wordpress.android.fluxc.model.list.ListItemModel
import org.wordpress.android.fluxc.model.list.ListModel
import org.wordpress.android.fluxc.model.list.ListType.POST
import org.wordpress.android.fluxc.persistence.ListItemSqlUtils
import org.wordpress.android.fluxc.persistence.ListSqlUtils
import org.wordpress.android.fluxc.persistence.WellSqlConfig
import java.util.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ListItemSqlUtilsTest {
    private lateinit var listSqlUtils: ListSqlUtils
    private lateinit var listItemSqlUtils: ListItemSqlUtils

    @Before
    fun setUp() {
        val appContext = RuntimeEnvironment.application.applicationContext
        val config = WellSqlConfig(appContext)
        WellSql.init(config)
        config.reset()

        listSqlUtils = ListSqlUtils()
        listItemSqlUtils = ListItemSqlUtils()
    }

    @Test
    fun testInsertItemList() {
        // Insert an item list for the passed in [ListDescriptor] and assert that it's inserted correctly
        generateInsertAndAssertListItems(ListDescriptor(POST, 333))
    }

    @Test
    fun testListIdForeignKeyCascadeDelete() {
        val listDescriptor = ListDescriptor(POST, 333)
        val testList = generateInsertAndAssertListItems(listDescriptor)

        /**
         * 1. Delete the inserted list
         * 2. Verify that deleting the list also deletes the inserted [ListItemModel]s due to foreign key restriction
         */
        listSqlUtils.deleteList(listDescriptor)
        assertEquals(0, listItemSqlUtils.getListItems(testList.id).size)
    }

    @Test
    fun testDeleteItem() {
        val testRemoteItemId = 1245L // value doesn't matter
        val listDescriptor1 = ListDescriptor(POST, 333)
        val listDescriptor2 = ListDescriptor(POST, 444)

        /**
         * 1. Insert a test list for 2 different list descriptors.
         * 2. Generate and insert a [ListItemModel] for both lists
         * 3. Verify that the [ListItemModel] was inserted correctly
         */
        val testLists = listOf(listDescriptor1, listDescriptor2).map { insertTestList(it) }
        val itemList = testLists.map { ListItemModel(it.id, testRemoteItemId) }
        listItemSqlUtils.insertItemList(itemList)
        testLists.forEach { list ->
            assertEquals(1, listItemSqlUtils.getListItems(list.id).size)
        }

        /**
         * 1. Delete [ListItemModel]s for which [ListItemModel.remoteItemId] == `testRemoteItemId`
         * 2. Verify that [ListItemModel]s from both lists are deleted
         */
        listItemSqlUtils.deleteItem(testLists.map { it.id }, testRemoteItemId)
        testLists.forEach {
            assertEquals(0, listItemSqlUtils.getListItems(it.id).size)
        }
    }

    @Test
    fun testDeleteItems() {
        /**
         * 1. Insert a test list with default number of items and assert that its inserted correctly
         * 2. Delete all items for a list
         * 3. Verify that list items is empty
         */
        val testList = generateInsertAndAssertListItems(ListDescriptor(POST, 333))
        listItemSqlUtils.deleteItems(testList.id)
        assertEquals(0, listItemSqlUtils.getListItems(testList.id).size)
    }

    @Test
    fun testDeleteItemsFromLists() {
        /**
         * 1. Create random 20 lists and 300 random items for these lists
         * 2. Insert the lists and the items in the DB
         * 3. Verify that they are inserted correctly
         */
        val rand = Random()
        val listIds = (1..20)
        val lists = listIds.map { insertTestList(ListDescriptor(POST, it)) }
        val items = (1..300L).map { ListItemModel(listId = rand.nextInt(listIds.count()) + 1, remoteItemId = it) }
        listItemSqlUtils.insertItemList(items)
        items.groupBy { it.listId }.forEach { listId, insertedItems ->
            assertEquals(insertedItems.size, listItemSqlUtils.getListItems(listId).size)
        }

        /**
         * 1. Pick 100 items to delete and 10 lists to delete from
         * 2. Delete the combination of selected lists and items
         * 3. If a list is picked to be deleted from, verify that remaining items don't contain items that should be
         * deleted.
         * 4. Verify that the remaining items are unchanged for lists that are not picked to be deleted from.
         */
        val remoteItemIdsToDelete = items.map { it.remoteItemId }.shuffled().take(100)
        val listIdsToDeleteFrom = lists.map { it.id }.shuffled().take(10)
        listItemSqlUtils.deleteItemsFromLists(listIdsToDeleteFrom, remoteItemIdsToDelete)
        items.groupBy { it.listId }.forEach { (listId, itemList) ->
            val remainingItems = listItemSqlUtils.getListItems(listId)
            if (listIdsToDeleteFrom.contains(listId)) {
                assertFalse(remainingItems.any { remoteItemIdsToDelete.contains(it.remoteItemId) })
            } else {
                assertTrue {
                    // `contains` approach wouldn't work here since the `id` fields might be different
                    itemList.zip(remainingItems).fold(true) { acc, (first, second) ->
                        acc && first.contentEquals(second)
                    }
                }
            }
        }
    }

    @Test
    fun insertDuplicateListItemModel() {
        val testRemoteItemId = 1245L // value doesn't matter

        /**
         * 1. Since a [ListItemModel] requires a [ListModel] in the DB due to the foreign key restriction, a test list
         * will be inserted in the DB.
         * 2. Generate 2 [ListItemModel]s with the exact same values and insert the first one in the DB
         * 3. Verify that first [ListItemModel] is inserted correctly
         */
        val testList = insertTestList(ListDescriptor(POST, 333))
        val listItemModel = ListItemModel(testList.id, testRemoteItemId)
        val listItemModel2 = ListItemModel(testList.id, testRemoteItemId)
        listItemSqlUtils.insertItemList(listOf(listItemModel))
        val insertedItemList = listItemSqlUtils.getListItems(testList.id)
        assertEquals(1, insertedItemList.size)

        /**
         * 1. Insert the second [ListItemModel] in the DB
         * 2. Verify that no new record is created and the list size is the same.
         * 3. Verify that the [ListItemModel.id] has not changed
         */
        listItemSqlUtils.insertItemList(listOf(listItemModel2))
        val updatedItemList = listItemSqlUtils.getListItems(testList.id)
        assertEquals(1, updatedItemList.size)
        assertEquals(insertedItemList[0].id, updatedItemList[0].id)
    }

    private fun generateInsertAndAssertListItems(listDescriptor: ListDescriptor, count: Int = 20): ListModel {
        /**
         * 1. Since a [ListItemModel] requires a [ListModel] in the DB due to the foreign key restriction, a test list
         * will be inserted in the DB.
         * 2. A list of test [ListItemModel]s will be generated and inserted in the DB
         * 3. Verify that the [ListItemModel] instances are inserted correctly
         */
        val testList = insertTestList(listDescriptor)
        val itemList = generateItemList(testList, count)
        listItemSqlUtils.insertItemList(itemList)
        assertEquals(count, listItemSqlUtils.getListItems(testList.id).size)
        return testList
    }

    /**
     * Creates and inserts a [ListModel] for the given [ListDescriptor].
     * It also asserts that the list is inserted correctly.
     */
    private fun insertTestList(listDescriptor: ListDescriptor): ListModel {
        listSqlUtils.insertOrUpdateList(listDescriptor)
        val list = listSqlUtils.getList(listDescriptor)
        assertNotNull(list)
        return list!!
    }

    /**
     * Helper function that creates a list of [ListItemModel] to be used in tests.
     */
    private fun generateItemList(listModel: ListModel, count: Int): List<ListItemModel> =
            (1..count).map { ListItemModel(listModel.id, it.toLong()) }
}
