# Запуск
    javac MatMul.java
    java MatMul 1000 1000 1000 8            # один запуск: n m k T
    java MatMul 1000 1000 1000 8 ijt        # то же, но школьный порядок циклов
    java MatMul loops 1500                  # сравнение порядков циклов, 1 поток
    java MatMul bench 500,1000,2000 5       # серия замеров -> results.csv
    pip install matplotlib
    python plot.py <физ_ядра> <логич_процессоры>   # графики + table.md
