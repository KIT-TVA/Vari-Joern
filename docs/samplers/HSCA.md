# The HSCA Sampler

This sampler returns a sample that achieves t-wise feature interaction coverage. Internally, it uses
the [HSCA](https://github.com/chuanluocs/HSCA) sampler. To enable statistical analysis, the internally used random
number generator is initialized with a random seed.

## Configuration

The HSCA sampler is configured using the following options:

- `t`
    - The parameter t for t-wise feature interaction coverage (HSCA only supports t >= 2).
    - Optional: no
- `L`
    - The termination criterion for the first optimization pass.
    - Optional: yes
    - Default: 5000
- `cutoff-time`
    - The cutoff time for the second optimization pass in seconds.
    - Optional: yes
    - Default: 1000
- `use-group`
    - Whether to use variable grouping strategy.
    - Optional: yes
    - Default: true
- `use-priority`
    - Whether to use dynamic priority assigning technique.
    - Optional: yes
    - Default: true
- `use-second-optimization`
    - Controls if the second optimization pass is enabled.
    - Optional: yes
    - Default: true

For example, the sampler could be configured as follows:

```toml
[product.sampler]
name = "hsca"
t = 2
L = 4000
cutoff-time = 30
use-group = true
use-priority = true
use-second-optimization = false
```