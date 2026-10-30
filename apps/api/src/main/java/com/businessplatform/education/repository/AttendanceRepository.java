package com.businessplatform.education.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.businessplatform.education.domain.*;

import jakarta.persistence.LockModeType;

public interface AttendanceRepository extends JpaRepository<AttendanceMark, UUID> {

    List<AttendanceMark> findByClassIdAndDay(UUID classId, LocalDate day);

    Optional<AttendanceMark> findByClassIdAndStudentIdAndDay(UUID classId, UUID studentId, LocalDate day);

    List<AttendanceMark> findByStudentIdAndDayBetweenOrderByDayDesc(UUID studentId, LocalDate from, LocalDate to);

    List<AttendanceMark> findByClassIdInAndDayBetween(Collection<UUID> classIds, LocalDate from, LocalDate to);

    List<AttendanceMark> findByOrgIdAndDayBetween(UUID orgId, LocalDate from, LocalDate to);

    /** Classes (among {@code classIds}) with marks on {@code day}. */
    @Query("SELECT DISTINCT a.classId FROM AttendanceMark a WHERE a.classId IN :classIds AND a.day = :day")
    List<UUID> classesMarked(Collection<UUID> classIds, LocalDate day);
}
