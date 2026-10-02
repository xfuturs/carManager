package com.carmanager.app.ownership

import androidx.lifecycle.ViewModel
import com.carmanager.app.core.ui.WorkspaceViewModelStores
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkspaceViewModelStoresTest {
    private class Form : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Test fun `same owner reuses store during activity recreation`() {
        val stores = WorkspaceViewModelStores()
        val first = stores.forOwner("firebase:A")
        val form = Form()
        first.viewModelStore.put("form", form)
        assertSame(first, stores.forOwner("firebase:A"))
        assertSame(form, first.viewModelStore["form"])
        assertFalse(form.cleared)
    }

    @Test fun `owner transition clears all previous form state`() {
        val stores = WorkspaceViewModelStores()
        val previous = stores.forOwner("firebase:A")
        val form = Form()
        previous.viewModelStore.put("form", form)
        val guest = stores.forOwner("guest:local")
        assertTrue(form.cleared)
        assertNull(previous.viewModelStore["form"])
        assertNull(guest.viewModelStore["form"])
        assertNotSame(previous, stores.forOwner("firebase:A"))
    }
}
