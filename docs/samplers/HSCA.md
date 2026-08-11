# The HSCA Sampler

This sampler returns a sample that achieves t-wise feature interaction coverage. Internally, it uses 
the [HSCA](https://github.com/chuanluocs/HSCA) sampler. To enable statistical analysis, the internally used random 
number generator is initialized with a random seed.

## Configuration

The HSCA sampler is configured using the following options:

- `t`
  - The parameter t for t-wise feature interaction coverage.
  - Optional: no
- `l`
  - The termination criterion for the first optimization pass.
  - Optional: yes
  - Default: 5000
- `cutoff-time`
  - The cutoff time for the second optimization pass.
  - Optional: yes
  - Default: 15
- `use-second-optimization`
  - Controls if the second optimization pass is enabled
  - Optional: yes
  - Default: true

For example, the sampler could be configured as follows:

```toml
[product.sampler]
name = "hsca"
t = 2
l = 4000
cutoff-time = 30
use-second-optimization = false
```