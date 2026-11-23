package com.example.library.config;

import java.util.List;

import com.example.library.domain.Gender;
import com.example.library.domain.Reader;
import com.example.library.domain.ReaderStatus;

/**
 * 首次启动时导入的示例读者：8 位，用于演示借阅、归还与逾期效果。
 */
public final class DemoReaders {

    /** 一位示例读者的基本信息。 */
    public record Spec(String cardNo, String name, String gender, String phone, String email) {
    }

    private DemoReaders() {
    }

    /** 曾由旧版本导入的示例读者，升级时会被清理掉。 */
    public static List<String> previousCardNos() {
        return List.of(
                "R-2026-001", "R-2026-002", "R-2026-003",
                "R-2026-004", "R-2026-005", "R-2026-006");
    }

    public static List<Spec> all() {
        return List.of(
                new Spec("R-2026-101", "林一诺", "女", "13900000101", "linyinuo@example.com"),
                new Spec("R-2026-102", "张伟", "男", "13900000102", "zhangwei@example.com"),
                new Spec("R-2026-103", "王芳", "女", "13900000103", "wangfang@example.com"),
                new Spec("R-2026-104", "李强", "男", "13900000104", "liqiang@example.com"),
                new Spec("R-2026-105", "刘洋", "男", "13900000105", "liuyang@example.com"),
                new Spec("R-2026-106", "陈晨", "女", "13900000106", "chenchen@example.com"),
                new Spec("R-2026-107", "杨帆", "男", "13900000107", "yangfan@example.com"),
                new Spec("R-2026-108", "赵雪", "女", "13900000108", "zhaoxue@example.com")
        );
    }

    public static Reader toEntity(Spec spec) {
        Reader reader = new Reader();
        reader.setCardNo(spec.cardNo());
        reader.setName(spec.name());
        reader.setPhone(spec.phone());
        reader.setEmail(spec.email());
        reader.setGender("女".equals(spec.gender()) ? Gender.FEMALE : Gender.MALE);
        reader.setStatus(ReaderStatus.ACTIVE);
        return reader;
    }
}
