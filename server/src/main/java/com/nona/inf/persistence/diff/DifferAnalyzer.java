package com.nona.inf.persistence.diff;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Changes;
import org.javers.core.diff.Change;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.NewObject;
import org.javers.core.diff.changetype.ObjectRemoved;
import org.javers.core.diff.changetype.PropertyChange;
import org.javers.core.diff.changetype.container.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 比较分析器，主要输出需要新增、删除、update的对象集合
 *
 * @author nona
 */
@Slf4j
@Getter
public class DifferAnalyzer {
    private final static DifferAnalyzer EMPTY_INSTANCE = new DifferAnalyzer(Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

    private final Set<Object> inserts;
    private final Set<Object> deletes;
    private final Set<Object> updates;

    private DifferAnalyzer(Set<Object> inserts, Set<Object> deletes, Set<Object> updates) {
        this.inserts = inserts;
        this.deletes = deletes;
        this.updates = updates;
    }

    public static DifferAnalyzer analyzeFromDiff(Diff diff) {
        if (!diff.hasChanges()) {
            return EMPTY_INSTANCE;
        }
        final DifferAnalyzer differAnalyzer = new DifferAnalyzer(new HashSet<>(), new HashSet<>(), new HashSet<>(8));
        // 顺序：删除 - 新增 - 修改
        final Changes changes = diff.getChanges();
        for (Change change : changes) {
            if (change.getAffectedObject().isEmpty()) {
                //  can not be here
                log.warn("no object available.this can not be happened,change is {}", change);
                continue;
            }
            final Object o = change.getAffectedObject().get();
            switch (change) {
                case NewObject ignore -> {
                    differAnalyzer.inserts.add(o);
                }
                case ObjectRemoved ignore -> {
                    differAnalyzer.deletes.add(o);
                }
                case PropertyChange<?> propertyChange -> {
                    differAnalyzer.updates.add(o);
                    handlePropChange(propertyChange, differAnalyzer);
                }
                default -> {
                    log.warn("unexpected change:{}", change);
                }
            }
        }
        return differAnalyzer;
    }

    private static void handlePropChange(PropertyChange<?> propertyChange, DifferAnalyzer differAnalyzer) {
        if (propertyChange instanceof ContainerChange<?> containerChange) {
            final List<ContainerElementChange> changes = containerChange.getChanges();
            for (ContainerElementChange change : changes) {
                if (change instanceof ValueAdded) {
                    System.out.println("add " + ((ValueAdded) change).getAddedValue());
                }
                if (change instanceof ValueRemoved) {
                    System.out.println("remove" + ((ValueRemoved) change).getRemovedValue());
                }
                if (change instanceof ElementValueChange) {
                    System.out.println("update" + ((ElementValueChange) change).getLeftValue() + "-> " + ((ElementValueChange) change).getRightValue());
                }
            }
        }
    }
}
