use std::iter::FusedIterator;

#[derive(Default, Clone, Copy, PartialEq, Eq, Hash, Debug)]
pub struct Bitmap32 {
    data: u32,
}

impl Bitmap32 {
    pub const EMPTY: Bitmap32 = Bitmap32 { data: 0 };

    pub const fn from_mask(mask: u32) -> Self {
        Self { data: mask }
    }

    pub fn set(&mut self, idx: usize) {
        self.data |= 1 << idx;
    }

    pub fn test(self, idx: usize) -> bool {
        (self.data & (1 << idx)) != 0
    }

    pub fn indices(self) -> BitmapIndexIter32 {
        BitmapIndexIter32 { data: self.data }
    }
}

impl std::ops::BitOr for Bitmap32 {
    type Output = Bitmap32;

    fn bitor(self, rhs: Self) -> Self::Output {
        Bitmap32 {
            data: self.data | rhs.data,
        }
    }
}

impl std::ops::BitOrAssign for Bitmap32 {
    fn bitor_assign(&mut self, rhs: Self) {
        self.data |= rhs.data
    }
}

#[derive(Clone)]
pub struct BitmapIndexIter32 {
    data: u32,
}

impl Iterator for BitmapIndexIter32 {
    type Item = usize;

    fn next(&mut self) -> Option<Self::Item> {
        let data = self.data;
        if data == 0 {
            return None;
        }

        // index of the lowest set bit
        let index = data.trailing_zeros();

        // bit trick to clear the lowest set bit
        self.data = data & (data - 1);

        Some(index as usize)
    }
}

impl FusedIterator for BitmapIndexIter32 {}

// TODO : use std::fmt::from_fn after stabalization, tracked at
// https://github.com/rust-lang/rust/issues/117729

pub fn from_fn<F: Fn(&mut std::fmt::Formatter<'_>) -> std::fmt::Result>(f: F) -> FromFn<F> {
    FromFn(f)
}

/// Implements [`fmt::Debug`] and [`fmt::Display`] using a function.
///
/// Created with [`from_fn`].
pub struct FromFn<F>(F)
where
    F: Fn(&mut std::fmt::Formatter<'_>) -> std::fmt::Result;

impl<F> std::fmt::Debug for FromFn<F>
where
    F: Fn(&mut std::fmt::Formatter<'_>) -> std::fmt::Result,
{
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        (self.0)(f)
    }
}

impl<F> std::fmt::Display for FromFn<F>
where
    F: Fn(&mut std::fmt::Formatter<'_>) -> std::fmt::Result,
{
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        (self.0)(f)
    }
}
