package kdesign
import kotlin.test.*
class AutosaveTest {
    @Test fun editsDuringWriteRemainDirtyUntilNextAcknowledgement() {
        val s=AutosaveState();s.edited();val first=s.begin()!!
        s.edited();assertNull(s.begin())
        s.complete(first,true);assertTrue(s.dirty)
        val second=s.begin()!!;s.complete(second,true);assertFalse(s.dirty)
    }
    @Test fun failureRetainsLatestRevisionForRetry() {
        val s=AutosaveState();s.edited();val first=s.begin()!!
        s.complete(first,false);assertTrue(s.dirty);assertEquals(1,s.failures)
        s.edited();val retry=s.begin()!!;assertEquals(2,retry)
        s.complete(retry,true);assertFalse(s.dirty);assertEquals(0,s.failures);assertNull(s.begin())
    }
}
