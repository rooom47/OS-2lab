
import csv, sys
from collections import defaultdict
import matplotlib.pyplot as plt

phys, logical = int(sys.argv[1]), int(sys.argv[2])
data = defaultdict(dict)                       # data[size][threads] = time
for r in csv.DictReader(open("results.csv")):
    data[int(r["size"])][int(r["threads"])] = float(r["avg_sec"])

# таблицы: потоки, время, ускорение, эффективность
with open("table.md", "w", encoding="utf-8") as f:
    for n, d in sorted(data.items()):
        t1 = d[1]
        f.write(f"\n**Матрицы {n}x{n}**\n\n| Потоки | Среднее время, с | Ускорение | Эффективность |\n|---|---|---|---|\n")
        for T, t in sorted(d.items()):
            f.write(f"| {T} | {t:.3f} | {t1/t:.2f} | {t1/t/T:.2f} |\n")

def vlines():
    plt.axvline(phys, color="gray", ls="--", label=f"физ. ядра ({phys})")
    plt.axvline(logical, color="black", ls=":", label=f"логич. процессоры ({logical})")

plt.figure(figsize=(8, 5))
for n, d in sorted(data.items()):
    plt.plot(sorted(d), [d[T] for T in sorted(d)], marker="o", label=f"{n}x{n}")
plt.yscale("log"); plt.xlim(0, logical * 2 + 2)
vlines()
plt.title("Время умножения в зависимости от числа потоков")
plt.xlabel("Число потоков"); plt.ylabel("Время, с (лог. шкала)"); plt.grid(True, which="both", alpha=0.4); plt.legend()
plt.savefig("time.png", dpi=150)

plt.figure(figsize=(8, 5))
for n, d in sorted(data.items()):
    plt.plot(sorted(d), [d[1] / d[T] for T in sorted(d)], marker="o", label=f"{n}x{n}")
mx = max(max(d) for d in data.values())
plt.plot([1, logical * 2], [1, logical * 2], "r--", label="идеальное S = T")
vlines()
plt.xlim(0, logical * 2 + 2); plt.ylim(0, logical + 1)
plt.title("Ускорение в зависимости от числа потоков")
plt.xlabel("Число потоков"); plt.ylabel("Ускорение S(T) = t(1)/t(T)"); plt.grid(True); plt.legend()
plt.savefig("speedup.png", dpi=150)

plt.figure(figsize=(8, 5))
for n, d in sorted(data.items()):
    plt.plot(sorted(d), [d[1] / d[T] / T for T in sorted(d)], marker="o", label=f"{n}x{n}")
vlines()
plt.xlim(0, logical * 2 + 2)
plt.title("Эффективность в зависимости от числа потоков")
plt.xlabel("Число потоков"); plt.ylabel("Эффективность E(T) = S(T)/T"); plt.grid(True); plt.legend()
plt.savefig("efficiency.png", dpi=150)
print("Готово: time.png, speedup.png, efficiency.png, table.md")
