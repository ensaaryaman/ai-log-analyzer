package com.ailoganalyzer.domain;

/**
 * Yapay zekanın bir soruna atadığı öncelik. Şiddet sırasına göre (düşükten yükseğe)
 * tanımlıdır; böylece analizleri önceliğe göre sıralamak kolaylaşır.
 * İsimler AI'ın döndürdüğü string'lerle birebir eşleşmelidir.
 */
public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
