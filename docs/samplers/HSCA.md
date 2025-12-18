# The HSCA Sampler

This sampler returns a sample that achieves t-wise feature interaction coverage.
Internally, it uses the [HSCA](https://github.com/chuanluocs/HSCA) sampler.
To enable statistical analysis, the internally used random number generator
is initialized with a random seed.

## Configuration

The HSCA sampler is configured using the following options:

- `t`
  - the parameter t for t-wise coverage.
  - Optional: no
- `l`
  - the termination criterion for the first optimization pass.
  - Optional: yes
  - Default: 5000
- `cutoff-time`
  - the cutoff time for the second optimization pass.
  - Optional: yes
  - Default: 60

For example, the sampler could be configured as follows:

```toml
[product.sampler]
name = "hsca"
t = 2
l = 4000
cutoff-time = 30
```