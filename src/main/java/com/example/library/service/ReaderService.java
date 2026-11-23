package com.example.library.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.library.domain.LoanStatus;
import com.example.library.domain.Reader;
import com.example.library.domain.ReaderStatus;
import com.example.library.repository.LoanRepository;
import com.example.library.repository.ReaderRepository;

@Service
public class ReaderService {

    private final ReaderRepository readers;
    private final LoanRepository loans;

    public ReaderService(ReaderRepository readers, LoanRepository loans) {
        this.readers = readers;
        this.loans = loans;
    }

    @Transactional(readOnly = true)
    public Page<Reader> search(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size);
        if (keyword == null || keyword.isBlank()) {
            return readers.findAllByOrderByIdDesc(pageable);
        }
        return readers.search("%" + keyword.trim().toLowerCase() + "%", pageable);
    }

    @Transactional(readOnly = true)
    public Reader get(Long id) {
        return readers.findById(id)
                .orElseThrow(() -> new BusinessException("读者不存在（id=" + id + "）"));
    }

    @Transactional(readOnly = true)
    public List<Reader> findActive() {
        return readers.findByStatusOrderByNameAsc(ReaderStatus.ACTIVE);
    }

    @Transactional
    public Reader create(Reader form) {
        form.setId(null);
        if (readers.existsByCardNo(form.getCardNo())) {
            throw new BusinessException("借书证号 " + form.getCardNo() + " 已存在");
        }
        return readers.save(form);
    }

    @Transactional
    public Reader update(Long id, Reader form) {
        Reader reader = get(id);
        if (readers.existsByCardNoAndIdNot(form.getCardNo(), id)) {
            throw new BusinessException("借书证号 " + form.getCardNo() + " 已被其他读者使用");
        }
        reader.setCardNo(form.getCardNo());
        reader.setName(form.getName());
        reader.setPhone(form.getPhone());
        reader.setEmail(form.getEmail());
        reader.setGender(form.getGender());
        reader.setStatus(form.getStatus());
        reader.setMaxBorrow(form.getMaxBorrow());
        return readers.save(reader);
    }

    @Transactional
    public void delete(Long id) {
        Reader reader = get(id);
        if (loans.countByReaderIdAndStatus(id, LoanStatus.BORROWED) > 0) {
            throw new BusinessException("读者「" + reader.getName() + "」还有未归还的借阅记录，不能删除");
        }
        if (loans.countByReaderId(id) > 0) {
            throw new BusinessException("读者「" + reader.getName() + "」存在历史借阅记录，不能删除（可改为停用）");
        }
        readers.delete(reader);
    }
}
