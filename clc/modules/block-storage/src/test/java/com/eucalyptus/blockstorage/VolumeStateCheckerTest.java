package com.eucalyptus.blockstorage;

import com.eucalyptus.blockstorage.entities.VolumeInfo;
import com.eucalyptus.blockstorage.util.StorageProperties;
import com.eucalyptus.entities.Entities;
import com.eucalyptus.entities.TransactionResource;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.junit.*;

import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertTrue;

/**
 *
 */
public class VolumeStateCheckerTest {

    @Rule public JUnitRuleMockery context = new JUnitRuleMockery();

    @BeforeClass
    public static void setupClass() {
        try {
            BlockStorageUnitTestSupport.setupBlockStoragePersistenceContext();
            BlockStorageUnitTestSupport.setupAuthPersistenceContext();
            BlockStorageUnitTestSupport.initializeAuth(1, 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @After
    public void teardown() {
        BlockStorageUnitTestSupport.flushSnapshotInfos();
        BlockStorageUnitTestSupport.flushVolumeInfos();
    }

    @AfterClass
    public static void teardownClass() {
        BlockStorageUnitTestSupport.tearDownBlockStoragePersistenceContext();
        BlockStorageUnitTestSupport.tearDownAuthPersistenceContext();
    }

    @Test
    public void runBasicTest() throws Exception {
        final LogicalStorageManager storageManager = context.mock(LogicalStorageManager.class);
        context.checking(new Expectations(){{
            exactly(3).of(storageManager).checkVolume(with(any(String.class)));
        }});

        VolumeInfo good = new VolumeInfo();
        good.setStatus(StorageProperties.Status.available.toString());
        good.setSize(new Integer(1));
        good.setUserName("unittestuser0");
        good.setVolumeId("vol-0000");
        good.setSnapshotId("snap-0000");
        good.setCreateTime(new Date());
        good.setZone("eucalyptus");

        VolumeInfo failOne = new VolumeInfo();
        failOne.setStatus(StorageProperties.Status.available.toString());
        failOne.setSize(new Integer(1));
        failOne.setUserName("unittestuser0");
        failOne.setVolumeId("vol-0001");
        failOne.setSnapshotId("snap-0001");
        failOne.setCreateTime(new Date());
        failOne.setZone("eucalyptus");

        VolumeInfo failTwo = new VolumeInfo();
        failTwo.setStatus(StorageProperties.Status.available.toString());
        failTwo.setSize(new Integer(1));
        failTwo.setUserName("unittestuser0");
        failTwo.setVolumeId("vol-0002");
        failTwo.setSnapshotId("snap-0002");
        failTwo.setCreateTime(new Date());
        failTwo.setZone("eucalyptus");

        try (TransactionResource tran = Entities.transactionFor(VolumeInfo.class)) {
            good = Entities.persist(good);
            failOne = Entities.persist(failOne);
            failTwo = Entities.persist(failTwo);
            tran.commit();
        }

        VolumeStateChecker volumeStateChecker = new VolumeStateChecker(storageManager);
        volumeStateChecker.run();

        List<VolumeInfo> remaining ;
        try (TransactionResource tran = Entities.transactionFor(VolumeInfo.class)) {
            remaining = Entities.query(new VolumeInfo());
            tran.commit();
        }

        assertTrue("expected to have a result set querying the eucalyptus_storage persistence context",
                remaining != null);
        assertTrue("expected all three VolumeInfos to still exist, but " + remaining.size() + " exist",
                remaining.size() == 3);
    }

}
