# LS-Sampling-Plus

This sampler chooses a sample of configurations using local search as implemented in
[LS-Sampling-Plus](https://github.com/chuanluocs/LS-Sampling-Plus).
To enable statistical analysis, the random number generator used internally is
initialized with a random seed.

## Configuration

LS-Sampling-Plus is configured using the following options:

- `sample-size`
    - The number of configurations to generate.
    - Optional: yes
    - Default: 100
- `t`
    - The t-wise feature interaction coverage to aim for (LS-Sampling-Plus only supports t >= 2).
    - Optional: yes
    - Default: 2
- `lambda`
    - The number of candidates per iteration.
    - A higher value results in better t-wise feature interaction coverage but higher runtime.
    - Optional: yes
    - Default: 100
- `delta`
    - The cardinality of the measuring set.
    - A higher value results in better t-wise feature interaction coverage but higher runtime.
    - Optional: yes
    - Default: 1.000.000
- `dynamic-prob`
    - Whether sampling probabilities should be updated dynamically.
    - Optional: yes
    - Default: true

For example, LS-Sampling-Plus can be configured as follows:

```toml
[product.sampler]
name = "ls-sampling-plus"
sample-size = 10
t = 2
lambda = 75
delta = 1500000
dynamic-prob = true
```