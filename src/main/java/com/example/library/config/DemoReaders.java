package com.example.library.config;

import java.util.List;

import com.example.library.domain.Reader;
import com.example.library.domain.ReaderStatus;

/**
 * 首次启动时导入的示例读者：6 位，只用于演示借阅记录。
 */
public final class DemoReaders {

    /** 一位示例读者的基本信息。 */
    public record Spec(String cardNo, String name, String phone) {
    }

    private DemoReaders() {
    }

    public static List<Spec> all() {
        return List.of(
                new Spec("R-2026-001", "陈小明", "13800000001"),
                new Spec("R-2026-002", "李静", "13800000002"),
                new Spec("R-2026-003", "王磊", "13800000003"),
                new Spec("R-2026-004", "赵敏", "13800000004"),
                new Spec("R-2026-005", "孙浩然", "13800000005"),
                new Spec("R-2026-006", "周雨桐", "13800000006")
        );
    }

    public static Reader toEntity(Spec spec) {
        Reader reader = new Reader();
        reader.setCardNo(spec.cardNo());
        reader.setName(spec.name());
        reader.setPhone(spec.phone());
        reader.setStatus(ReaderStatus.ACTIVE);
        return reader;
    }
}
